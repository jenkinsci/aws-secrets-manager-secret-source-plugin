package io.jenkins.plugins.casc.secretsmanager;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.secretsmanager.util.AWSSecretsManagerRule;
import io.jenkins.plugins.casc.secretsmanager.util.CredentialNames;
import io.jenkins.plugins.casc.secretsmanager.util.DeferredEnvironmentVariables;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.jvnet.hudson.test.JenkinsRule;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

public class SecretSourceIT {

    private static final String SECRET_STRING = "supersecret";
    private static final byte[] SECRET_BINARY = {0x01, 0x02, 0x03};

    public final AWSSecretsManagerRule secretsManager = new AWSSecretsManagerRule();

    public final JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public final RuleChain chain = RuleChain
            .outerRule(secretsManager)
            .around(new DeferredEnvironmentVariables()
                    .set("AWS_ACCESS_KEY_ID", "fake")
                    .set("AWS_SECRET_ACCESS_KEY", "fake")
                    .set("AWS_REGION", "us-east-1")
                    .set("AWS_ENDPOINT_URL", secretsManager::getServiceEndpoint))
            .around(jenkins);

    private ConfigurationContext context;

    @Before
    public void refreshConfigurationContext() {
        final var registry = ConfiguratorRegistry.get();
        context = new ConfigurationContext(registry);
    }

    /**
     * Note: When Secrets Manager is unavailable, the AWS SDK treats this the same as '404 not found'.
     */
    @Test
    public void shouldReturnEmptyWhenSecretWasNotFound() {
        // When
        final var secret = revealSecret("foo");

        // Then
        assertThat(secret).isEmpty();
    }

    @Test
    public void shouldRevealSecret() {
        // Given
        final var foo = createSecret(SECRET_STRING);

        // When
        final var secret = revealSecret(foo.name());

        // Then
        assertThat(secret).isEqualTo(SECRET_STRING);
    }

    @Test
    public void shouldThrowExceptionWhenSecretWasSoftDeleted() {
        final var foo = createSecret(SECRET_STRING);
        deleteSecret(foo.name());

        assertThatIOException()
                .isThrownBy(() -> revealSecret(foo.name()));
    }

    @Test
    public void shouldThrowExceptionWhenSecretWasBinary() {
        final var foo = createSecret(SECRET_BINARY);

        assertThatIOException()
                .isThrownBy(() -> revealSecret(foo.name()));
    }

    private CreateSecretResponse createSecret(String secretString) {
        final var request = CreateSecretRequest.builder()
                .name(CredentialNames.random())
                .secretString(secretString)
                .build();

        return secretsManager.getClient().createSecret(request);
    }

    private CreateSecretResponse createSecret(byte[] secretBinary) {
        final var request = CreateSecretRequest.builder()
                .name(CredentialNames.random())
                .secretBinary(SdkBytes.fromByteArray(secretBinary))
                .build();

        return secretsManager.getClient().createSecret(request);
    }

    private void deleteSecret(String secretId) {
        final var request = DeleteSecretRequest.builder().secretId(secretId).build();
        secretsManager.getClient().deleteSecret(request);
    }

    private String revealSecret(String id) {
        return context.getSecretSourceResolver().resolve("${" + id + "}");
    }
}
