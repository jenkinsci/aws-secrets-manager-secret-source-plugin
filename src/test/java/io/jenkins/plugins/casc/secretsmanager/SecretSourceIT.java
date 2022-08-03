package io.jenkins.plugins.casc.secretsmanager;

import com.amazonaws.arn.Arn;
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
    public void shouldRevealSecretWhenReferencedByName() {
        // Given
        final CreateSecretResult foo = createSecret(STRING_SECRET_VALUE);

        // When
        final String secret = revealSecret(foo.getName());

        // Then
        assertThat(secret).isEqualTo(STRING_SECRET_VALUE);
    }

    @Test
    public void shouldRevealSecretWhenReferencedByArn() {
        // Given
        final CreateSecretResult foo = createSecret(STRING_SECRET_VALUE);

        // When
        final String arn = arn(foo);
        final String secret = revealSecret(arn);

        // Then
        assertThat(secret).isEqualTo(STRING_SECRET_VALUE);
    }

    @Test
    public void shouldRevealJsonSecretWhenReferencedByArnAndKey() {
        // Given
        final CreateSecretResult foo = createSecret(JSON_SECRET_VALUE);

        // When
        final String arn = jsonArn(foo, "someJsonKey");
        final String secret = revealSecret(arn);

        // Then
        assertThat(secret).isEqualTo(STRING_SECRET_VALUE);
    }

    @Test
    public void shouldThrowExceptionWhenJsonSecretContainsInvalidJson() {
        final CreateSecretResult foo = createSecret(JSON_SECRET_VALUE_BROKEN);

        final String arn = jsonArn(foo, "someJsonKey");

        assertThatIOException()
                .isThrownBy(() -> revealSecret(arn));
    }

    @Test
    public void shouldThrowExceptionWhenArnIsInvalid() {
        createSecret(STRING_SECRET_VALUE);

        assertThatIOException()
            .isThrownBy(() -> revealSecret(INVALID_SECRET_ARN));
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

    private String arn(CreateSecretResult result) {
        // The result of `getARN()` may have a suffix of a hyphen and 6 random characters
        // The Moto SecretsManager mock doesn't handle those (yet), so remove it
        final Arn originalArn = Arn.fromString(result.getARN());

        return originalArn.toBuilder()
                .withResource("secret:" + result.getName())
                .build()
                .toString();
    }

    private String jsonArn(CreateSecretResult result, String key) {
        // The result of `getARN()` may have a suffix of a hyphen and 6 random characters
        // The Moto SecretsManager mock doesn't handle those (yet), so remove it
        final Arn originalArn = Arn.fromString(result.getARN());

        return originalArn.toBuilder()
                .withResource("secret:" + result.getName() + ":" + key)
                .build()
                .toString();
    }
}
