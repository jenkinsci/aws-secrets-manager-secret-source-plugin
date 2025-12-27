package io.jenkins.plugins.casc.secretsmanager;

import hudson.Extension;
import io.jenkins.plugins.casc.SecretSource;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class AwsSecretsManagerSecretSource extends SecretSource {

    private static final Logger LOG = Logger.getLogger(AwsSecretsManagerSecretSource.class.getName());

    /**
     * In AWS SDK V2, this property is supported as standard and is called AWS_ENDPOINT_URL.
     * <p>
     * We support the old name of the property for backward compatibility, so that Jenkins installations which used
     * older versions of this plugin can keep working.
     */
    @Deprecated
    private static final String AWS_SERVICE_ENDPOINT = "AWS_SERVICE_ENDPOINT";

    private transient SecretsManagerClient client = null;

    @Override
    public Optional<String> reveal(String id) throws IOException {
        try {
            final var request = GetSecretValueRequest.builder().secretId(id).build();
            final var result = client.getSecretValue(request);

            if (result.secretBinary() != null) {
                throw new IOException(String.format("The binary secret '%s' is not supported. Please change its value to a string, or alternatively delete it.", result.name()));
            }

            return Optional.ofNullable(result.secretString());
        } catch (ResourceNotFoundException e) {
            // Recoverable errors
            LOG.info(e.getMessage());
            return Optional.empty();
        } catch (SecretsManagerException e) {
            // Unrecoverable errors
            LOG.warning(e.getMessage());
            throw new IOException(e);
        }
    }

    @Override
    public void init() {
        try {
            client = createClient();
        } catch (SdkClientException e) {
            LOG.log(Level.WARNING, "Could not set up AWS Secrets Manager client. Reason: {0}", e.getMessage());
        }
    }

    private static SecretsManagerClient createClient() throws SdkClientException {
        final var builder = SecretsManagerClient.builder();

        // Provided for backwards compatibility
        final var maybeServiceEndpoint = getServiceEndpoint();
        if (maybeServiceEndpoint.isPresent()) {
            final var serviceEndpoint = maybeServiceEndpoint.get();

            LOG.log(Level.CONFIG, "Custom Service Endpoint: {0}", serviceEndpoint);

            builder.endpointOverride(URI.create(serviceEndpoint));
        }

        return builder.build();
    }

    private static Optional<String> getServiceEndpoint() {
        return Optional.ofNullable(System.getenv(AWS_SERVICE_ENDPOINT));
    }
}
