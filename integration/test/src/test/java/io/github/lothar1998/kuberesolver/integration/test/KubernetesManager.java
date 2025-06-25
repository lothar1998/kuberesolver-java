package io.github.lothar1998.kuberesolver.integration.test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.discovery.v1.Endpoint;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.NamespaceListVisitFromServerGetDeleteRecreateWaitApplicable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;

public class KubernetesManager {
    private static final String SERVER_DEPLOYMENT_NAME = "server";
    private static final String CLIENT_DEPLOYMENT_NAME = "client";

    private static final int CLIENT_HTTP_SERVER_PORT = 50052;

    private final KubernetesClient client;
    private final NamespaceListVisitFromServerGetDeleteRecreateWaitApplicable<HasMetadata> allResources;

    public KubernetesManager(KubernetesClient client) {
        this.client = client;
        var manifests = KubernetesManager.class.getClassLoader().getResourceAsStream("k8s-manifests.yaml");
        this.allResources = client.load(manifests);
    }

    public void startEnvironment() {
        allResources.serverSideApply();
        awaitReadyDeployment(CLIENT_DEPLOYMENT_NAME);
        awaitReadyDeployment(SERVER_DEPLOYMENT_NAME);
    }

    public void stopEnvironment() {
        allResources.delete();
    }

    public Set<String> scaleServer(int replicasCount) {
        client.apps().deployments()
                .withName(SERVER_DEPLOYMENT_NAME)
                .scale(replicasCount);

        return awaitScaledReadyDeployment(SERVER_DEPLOYMENT_NAME, replicasCount)
                .stream()
                .flatMap(e -> e.getAddresses().stream())
                .collect(Collectors.toSet());
    }

    public void awaitReadyDeployment(String deploymentName) {
        awaitScaledReadyDeployment(deploymentName, 1);
    }

    public List<Endpoint> awaitScaledReadyDeployment(String deploymentName, int replicasCount) {
        return await()
                .atMost(1, TimeUnit.MINUTES)
                .until(
                        () -> client.discovery().v1().endpointSlices()
                                .withLabel("app", deploymentName)
                                .list()
                                .getItems()
                                .stream()
                                .map(EndpointSlice::getEndpoints)
                                .filter(Objects::nonNull)
                                .flatMap(Collection::stream)
                                .filter(e -> e != null && e.getConditions() != null && e.getConditions().getReady())
                                .toList(),
                        readyEndpoints -> (long) readyEndpoints.size() == replicasCount
                );
    }

    public LocalPortForward portForwardToClient() {
        Pod pod = await()
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> client.pods()
                                .withLabel("app", CLIENT_DEPLOYMENT_NAME)
                                .list()
                                .getItems()
                                .stream()
                                .filter(p -> "Running".equals(p.getStatus().getPhase()))
                                .findFirst()
                                .orElse(null),
                        Objects::nonNull);

        return client.pods()
                .withName(pod.getMetadata().getName())
                .portForward(CLIENT_HTTP_SERVER_PORT);
    }
}
