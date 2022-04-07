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

    private static final String SECRET_NAME = "my-secret";

    private static final String SECRET_ARN = "arn:aws:secretsmanager:eu-central-1:123456789012:secret:my-secret";
    private static final String SECRET_ARN_WITH_JSON_KEY = "arn:aws:secretsmanager:eu-central-1:123456789012:secret:my-secret:someJsonKey";
    private static final String INVALID_SECRET_ARN = "arn:aws:secretsmanager";

    private static final String STRING_SECRET_VALUE = "secretValue";
    private static final byte[] BINARY_SECRET_VALUE = {0x01, 0x02, 0x03};
    private static final String JSON_SECRET_VALUE = "{\"someJsonKey\": \"secretValue\"}";
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
    public void shouldRevealSecretValueWhenPlainTextSecretIsReferencedViaArn() {
        // Given
        createSecret(STRING_SECRET_VALUE, SECRET_NAME);

        // When
        final String secret = revealSecret(SECRET_ARN);

        // Then
        assertThat(secret).isEqualTo(STRING_SECRET_VALUE);
    }

    @Test
    public void shouldRevealSecretValueWhenPlainTextSecretIsReferencedViaName() {
        // Given
        createSecret(STRING_SECRET_VALUE, SECRET_NAME);

        // When
        final String secret = revealSecret(SECRET_NAME);

        // Then
        assertThat(secret).isEqualTo(STRING_SECRET_VALUE);
    }

    @Test
    public void shouldRevealSecretValueWhenJsonSecretIsReferencedViaArnAndKey() {
        // Given
        createSecret(JSON_SECRET_VALUE, SECRET_NAME);

        // When
        final String secret = revealSecret(SECRET_ARN_WITH_JSON_KEY);

        // Then
        assertThat(secret).isEqualTo(STRING_SECRET_VALUE);
    }

    @Test
    public void shouldRevealPlainTextSecretReferencedByRandomName() {
        // Given
        final CreateSecretResult foo = createSecret(STRING_SECRET_VALUE);

        // When
        final String secret = revealSecret(foo.getName());

        // Then
        assertThat(secret).isEqualTo(STRING_SECRET_VALUE);
    }

    @Test
    public void shouldThrowExceptionWhenSecretIsNotFound() {
        final CreateSecretResult foo = createSecret(STRING_SECRET_VALUE, SECRET_NAME);
        deleteSecret(foo.getName());

        assertThatIOException()
            .isThrownBy(() -> revealSecret(foo.getName()));
    }

    @Test
    public void shouldThrowExceptionWhenArnIsInvalid() {
        createSecret(STRING_SECRET_VALUE, SECRET_NAME);

        assertThatIOException()
            .isThrownBy(() -> revealSecret(INVALID_SECRET_ARN));
    }

    @Test
    public void shouldThrowExceptionWhenJsonSecretContainsInvalidJson() {
        createSecret(JSON_SECRET_VALUE_BROKEN, SECRET_NAME);

        assertThatIOException()
            .isThrownBy(() -> revealSecret(SECRET_ARN_WITH_JSON_KEY));
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
