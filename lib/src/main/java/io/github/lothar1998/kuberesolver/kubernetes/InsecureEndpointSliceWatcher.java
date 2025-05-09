package io.github.lothar1998.kuberesolver.kubernetes;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpClient.Version;

/**
 * A watcher for Kubernetes EndpointSlices that communicates over HTTP without TLS (insecure).
 * This class extends {@link EndpointSliceWatcher} and provides an {@link HttpClient}
 * configured for HTTP/1.1 and basic JSON requests.
 * <p>
 * It is intended for development or trusted network environments where secure communication
 * is not required.
 */
public final class InsecureEndpointSliceWatcher extends EndpointSliceWatcher {

    /**
     * Constructs an insecure EndpointSliceWatcher with the specified Kubernetes API host and namespace.
     *
     * @param host      the hostname or IP of the Kubernetes API server
     * @param namespace the Kubernetes namespace to watch for EndpointSlices
     */
    public InsecureEndpointSliceWatcher(String host, String namespace) {
        super(host, namespace);
    }

    /**
     * Creates an {@link HttpClient} configured to use HTTP/1.1 without TLS.
     *
     * @return an insecure HTTP/1.1 {@link HttpClient} instance
     */
    @Override
    protected HttpClient getClient() {
        return HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .build();
    }

    /**
     * Builds an HTTP GET request to fetch EndpointSlice information for the given service name.
     *
     * @param serviceName the name of the Kubernetes service
     * @return an {@link HttpRequest} configured for the service's EndpointSlice
     * @throws Exception if URI creation fails
     */
    @Override
    protected HttpRequest getRequest(String serviceName) throws Exception {
        return HttpRequest.newBuilder(getURI(serviceName))
                .setHeader("Accept", "application/json")
                .build();
    }

}
