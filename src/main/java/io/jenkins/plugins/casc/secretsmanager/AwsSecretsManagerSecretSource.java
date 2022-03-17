package io.jenkins.plugins.casc.secretsmanager;

import com.amazonaws.SdkClientException;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClient;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.AWSSecretsManagerException;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Extension;
import io.jenkins.plugins.casc.SecretSource;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class AwsSecretsManagerSecretSource extends SecretSource {

    private static final Logger LOG = Logger.getLogger(AwsSecretsManagerSecretSource.class.getName());

    private static final String AWS_SERVICE_ENDPOINT = "AWS_SERVICE_ENDPOINT";
    private static final String AWS_SIGNING_REGION = "AWS_SIGNING_REGION";
    private static final String AWS_SECRET_NAME_SPLIT_CHARACTER = ">";

    private transient AWSSecretsManager client = null;

    @Override
    public Optional<String> reveal(String id) throws IOException {
        try {
            String secretName = id;
            String jsonKey = null;

            // Split id into secret name and json object key if a ">" exists.
            if(id.contains(AWS_SECRET_NAME_SPLIT_CHARACTER)) {
                String[] secretNameAndJsonKey = id.split(AWS_SECRET_NAME_SPLIT_CHARACTER);
                secretName = secretNameAndJsonKey[0];
                jsonKey = secretNameAndJsonKey[1];
            }

            final GetSecretValueResult result = client.getSecretValue(
                new GetSecretValueRequest().withSecretId(secretName));

            if (result.getSecretBinary() != null) {
                throw new IOException(String.format("The binary secret '%s' is not supported. Please change its value to a string, or alternatively delete it.", result.getName()));
            }

            String resultString = result.getSecretString();

            // The secret name and a key inside the json object are defined.
            // Secret is expected to be a json object.
            if (secretName != null && jsonKey != null) {
                ObjectMapper mapper = new ObjectMapper();
                TypeReference<Map<String, String>> typeRef = new TypeReference<Map<String, String>>() {};
                Map<String, String> map = mapper.readValue(resultString, typeRef);
                return Optional.ofNullable(map.get(jsonKey));
            } else {
                // Only secret name is specified.
                // The secret is expected to be a plain string.
                return Optional.ofNullable(resultString);
            }
        } catch (ResourceNotFoundException e) {
            // Recoverable errors
            LOG.info(e.getMessage());
            return Optional.empty();
        } catch (AWSSecretsManagerException | ArrayIndexOutOfBoundsException e) {
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

    private static AWSSecretsManager createClient() throws SdkClientException {
        final AWSSecretsManagerClientBuilder builder = AWSSecretsManagerClient.builder();

        final Optional<String> maybeServiceEndpoint = getServiceEndpoint();
        final Optional<String> maybeSigningRegion = getSigningRegion();

        if (maybeServiceEndpoint.isPresent() && maybeSigningRegion.isPresent()) {
            LOG.log(Level.CONFIG, "Custom Endpoint Configuration: {0}", maybeServiceEndpoint.get());

            final AwsClientBuilder.EndpointConfiguration endpointConfiguration =
                    new AwsClientBuilder.EndpointConfiguration(maybeServiceEndpoint.get(), maybeSigningRegion.get());
            builder.setEndpointConfiguration(endpointConfiguration);
        } else {
            LOG.log(Level.CONFIG, "Default Endpoint Configuration");
        }

        return builder.build();
    }

    private static Optional<String> getServiceEndpoint() {
        return Optional.ofNullable(System.getenv(AWS_SERVICE_ENDPOINT));
    }

    private static Optional<String> getSigningRegion() {
        return Optional.ofNullable(System.getenv(AWS_SIGNING_REGION));
    }
}
