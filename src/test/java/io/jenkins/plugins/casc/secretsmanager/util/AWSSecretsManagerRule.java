package io.jenkins.plugins.casc.secretsmanager.util;

import org.junit.rules.ExternalResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.net.URI;

/**
 * Wraps client-side access to AWS Secrets Manager in tests. Defers client initialization in case you want to set AWS
 * environment variables or Java properties in a wrapper Rule first.
 */
public class AWSSecretsManagerRule extends ExternalResource {

    private static final DockerImageName MOTO_IMAGE = DockerImageName.parse("motoserver/moto:5.1.18");

    private final GenericContainer<?> secretsManager = new GenericContainer<>(MOTO_IMAGE)
            .withExposedPorts(5000)
            .waitingFor(Wait.forHttp("/"));

    private transient SecretsManagerClient client;

    public String getServiceEndpoint() {
        final var host = secretsManager.getHost();
        final var port = secretsManager.getFirstMappedPort();
        return String.format("http://%s:%d", host, port);
    }

    @Override
    public void before() {
        secretsManager.start();

        final var host = secretsManager.getHost();
        final var port = secretsManager.getFirstMappedPort();
        final var serviceEndpoint = URI.create(String.format("http://%s:%d", host, port));

        client = SecretsManagerClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(serviceEndpoint)
                .build();
    }

    @Override
    protected void after() {
        client = null;
        secretsManager.stop();
    }

    public SecretsManagerClient getClient() {
        return client;
    }
}
