package io.jenkins.plugins.casc.secretsmanager;

import hudson.Extension;
import io.jenkins.plugins.casc.SecretSource;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class AwsSecretsManagerSecretSource extends SecretSource {

    private static final Logger LOG = Logger.getLogger(AwsSecretsManagerSecretSource.class.getName());

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
            client = SecretsManagerClient.create();
        } catch (SdkClientException e) {
            LOG.log(Level.WARNING, "Could not set up AWS Secrets Manager client. Reason: {0}", e.getMessage());
        }
    }
}
