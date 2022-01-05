package io.jenkins.plugins.casc.secretsmanager;

import com.amazonaws.services.secretsmanager.model.CreateSecretRequest;
import com.amazonaws.services.secretsmanager.model.CreateSecretResult;
import com.amazonaws.services.secretsmanager.model.DeleteSecretRequest;
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

import java.nio.ByteBuffer;

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
                    // Invent 2 environment variables which don't technically exist in AWS SDK
                    .set("AWS_SERVICE_ENDPOINT", secretsManager::getServiceEndpoint)
                    .set("AWS_SIGNING_REGION", secretsManager::getSigningRegion))
            .around(jenkins);

    private ConfigurationContext context;

    @Before
    public void refreshConfigurationContext() {
        final ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        context = new ConfigurationContext(registry);
    }

    /**
     * Note: When Secrets Manager is unavailable, the AWS SDK treats this the same as '404 not found'.
     */
    @Test
    public void shouldReturnEmptyWhenSecretWasNotFound() {
        // When
        final String secret = revealSecret("foo");

        // Then
        assertThat(secret).isEmpty();
    }

    @Test
    public void shouldRevealSecret() {
        // Given
        final CreateSecretResult foo = createSecret(SECRET_STRING);

        // When
        final String secret = revealSecret(foo.getName());

        // Then
        assertThat(secret).isEqualTo(SECRET_STRING);
    }

    @Test
    public void shouldThrowExceptionWhenSecretWasSoftDeleted() {
        final CreateSecretResult foo = createSecret(SECRET_STRING);
        deleteSecret(foo.getName());

        assertThatIOException()
                .isThrownBy(() -> revealSecret(foo.getName()));
    }

    @Test
    public void shouldThrowExceptionWhenSecretWasBinary() {
        final CreateSecretResult foo = createSecret(SECRET_BINARY);

        assertThatIOException()
                .isThrownBy(() -> revealSecret(foo.getName()));
    }

    private CreateSecretResult createSecret(String secretString) {
        final CreateSecretRequest request = new CreateSecretRequest()
                .withName(CredentialNames.random())
                .withSecretString(secretString);

        return secretsManager.getClient().createSecret(request);
    }

    private CreateSecretResult createSecret(byte[] secretBinary) {
        final CreateSecretRequest request = new CreateSecretRequest()
                .withName(CredentialNames.random())
                .withSecretBinary(ByteBuffer.wrap(secretBinary));

        return secretsManager.getClient().createSecret(request);
    }

    private void deleteSecret(String secretId) {
        final DeleteSecretRequest request = new DeleteSecretRequest().withSecretId(secretId);
        secretsManager.getClient().deleteSecret(request);
    }

    private String revealSecret(String id) {
        return context.getSecretSourceResolver().resolve("${" + id + "}");
    }
}
