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

    private static final String STRING_SECRET_VALUE = "supersecret";
    private static final byte[] BINARY_SECRET_VALUE = {0x01, 0x02, 0x03};
    private static final String JSON_SECRET_NAME = "secretName";
    private static final String JSON_SECRET_NAME_AND_KEY = "secretName>someKey";
    private static final String JSON_SECRET_NAME_KEY_AND_BROKEN = "secretName>";
    private static final String JSON_SECRET_VALUE = "{\"someKey\": \"someSecretValue\"}";
    private static final String JSON_SECRET_VALUE_BROKEN = "{Some broken Json []}";

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
        final CreateSecretResult foo = createSecret(STRING_SECRET_VALUE);

        // When
        final String secret = revealSecret(foo.getName());

        // Then
        assertThat(secret).isEqualTo(STRING_SECRET_VALUE);
    }

    @Test
    public void shouldRevealSecretFromJsonSecret() {
        // Given
        createSecret(JSON_SECRET_VALUE, JSON_SECRET_NAME);

        // When
        final String secret = revealSecret(JSON_SECRET_NAME_AND_KEY);

        // Then
        assertThat(secret).isEqualTo("someSecretValue");
    }

    @Test
    public void shouldThrowExceptionWhenJsonSecretNameKeyIsInvalid() {
        createSecret(JSON_SECRET_VALUE, JSON_SECRET_NAME);

        assertThatIOException()
            .isThrownBy(() -> revealSecret(JSON_SECRET_NAME_KEY_AND_BROKEN));
    }

    @Test
    public void shouldThrowExceptionWhenJsonSecretIsInvalidJson() {
        createSecret(JSON_SECRET_VALUE_BROKEN, JSON_SECRET_NAME);

        assertThatIOException()
            .isThrownBy(() -> revealSecret(JSON_SECRET_NAME_AND_KEY));
    }

    @Test
    public void shouldThrowExceptionWhenSecretWasSoftDeleted() {
        final CreateSecretResult foo = createSecret(STRING_SECRET_VALUE);
        deleteSecret(foo.getName());

        assertThatIOException()
                .isThrownBy(() -> revealSecret(foo.getName()));
    }

    @Test
    public void shouldThrowExceptionWhenSecretWasBinary() {
        final CreateSecretResult foo = createSecret(BINARY_SECRET_VALUE);

        assertThatIOException()
                .isThrownBy(() -> revealSecret(foo.getName()));
    }

    private CreateSecretResult createSecret(String secretString) {
        return createSecret(secretString, CredentialNames.random());
    }

    private CreateSecretResult createSecret(String secretString, String secretName) {
        final CreateSecretRequest request = new CreateSecretRequest()
            .withName(secretName)
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
