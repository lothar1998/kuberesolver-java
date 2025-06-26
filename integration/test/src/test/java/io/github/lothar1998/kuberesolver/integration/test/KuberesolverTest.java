package io.github.lothar1998.kuberesolver.integration.test;

import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Slf4j
public class KuberesolverTest {

    private static KubernetesManager manager;
    private LocalPortForward portForward;
    private IPsDiscoverer discoverer;

    @BeforeAll
    static void setupAll() {
        var config = new ConfigBuilder().build();
        log.info("Building Kubernetes client with context: {}", config.getCurrentContext().getName());
        var client = new KubernetesClientBuilder().build();
        manager = new KubernetesManager(client);
    }

    @BeforeEach
    void setup() {
        log.info("Setting up environment");
        manager.startEnvironment();
        portForward = manager.portForwardToClient();
        discoverer = new IPsDiscoverer(portForward.getLocalAddress(), portForward.getLocalPort());
    }

    @AfterEach
    void teardown() throws IOException {
        log.info("Tearing down environment");
        manager.stopEnvironment();
        portForward.close();
    }

    @DisplayName("should continuously resolve all addresses of deployment behind a service")
    @ParameterizedTest(name = "replicas changes = {0}")
    @MethodSource(value = "testCases")
    void continuouslyResolveAllAddressesTest(List<Integer> replicasHistory) {
        for (Integer replicas : replicasHistory) {
            log.info("Scaling server to {} replicas", replicas);
            final var serverIPs = manager.scaleServer(replicas);
            log.info("Replicas have the following IPs: {}", String.join(", ", serverIPs));

            discoverer.reset();

            await()
                    .atMost(1, TimeUnit.MINUTES)
                    .until(
                            () -> {
                                var ips = discoverer.getDiscoveredIPs();
                                if (!ips.isEmpty()) {
                                    log.info("Discovered the following IPs: {}", String.join(", ", ips));
                                }
                                return ips;
                            },
                            discoveredIPs -> discoveredIPs.equals(serverIPs)
                    );
        }
    }

    private static Stream<Arguments> testCases() {
        return Stream.of(
                List.of(1, 2, 3),
                List.of(3, 2, 1),
                List.of(1, 3, 2),
                List.of(3, 1, 2)
        ).map(Arguments::of);
    }
}
