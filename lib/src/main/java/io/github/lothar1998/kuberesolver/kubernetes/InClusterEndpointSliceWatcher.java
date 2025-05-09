package io.github.lothar1998.kuberesolver.kubernetes;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * A Kubernetes EndpointSlice watcher that runs from within a Kubernetes cluster.
 * <p>
 * This implementation reads in-cluster configuration including the API server host and port
 * from environment variables, and authentication details (token, CA cert, and namespace)
 * from service account files mounted in the pod.
 * <p>
 * It is suitable for production usage within a Kubernetes cluster and assumes the standard
 * in-cluster configuration paths and environment variables are present.
 */
public final class InClusterEndpointSliceWatcher extends SecureEndpointSliceWatcher {

    private static final String KUBERNETES_SERVICE_HOST = "KUBERNETES_SERVICE_HOST";
    private static final String KUBERNETES_SERVICE_PORT = "KUBERNETES_SERVICE_PORT";

    private static final String KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token";
    private static final String KUBERNETES_SERVICE_ACCOUNT_CA_CERT_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";
    private static final String KUBERNETES_NAMESPACE_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/namespace";

    /**
     * Constructs the watcher by inferring the namespace from the in-cluster namespace file.
     *
     * @throws IOException if reading the namespace file fails
     */
    public InClusterEndpointSliceWatcher() throws IOException {
        this(getNamespace());
    }

    /**
     * Constructs the watcher using the provided namespace and other in-cluster configuration.
     *
     * @param namespace the Kubernetes namespace to watch
     */
    public InClusterEndpointSliceWatcher(String namespace) {
        super(getHost(), namespace, getAuthConfigProvider());
    }

    /**
     * Builds the Kubernetes API host URL using environment variables
     * {@code KUBERNETES_SERVICE_HOST} and {@code KUBERNETES_SERVICE_PORT}.
     *
     * @return the complete Kubernetes API server URL
     * @throws RuntimeException if required environment variables are not set
     */
    private static String getHost() {
        var address = Optional.of(System.getenv(KUBERNETES_SERVICE_HOST)).orElseThrow(
                () -> new RuntimeException(String.format("%s env variable not set", KUBERNETES_SERVICE_HOST)));

        var port = Optional.of(System.getenv(KUBERNETES_SERVICE_PORT)).orElseThrow(
                () -> new RuntimeException(String.format("%s env variable not set", KUBERNETES_SERVICE_PORT)));

        return String.format("https://%s:%s", address, port);
    }

    /**
     * Reads the namespace from the in-cluster service account namespace file.
     *
     * @return the current namespace, or "default" if the file is not found
     * @throws IOException if file reading fails
     */
    private static String getNamespace() throws IOException {
        try {
            return Files.readString(Paths.get(KUBERNETES_NAMESPACE_PATH), StandardCharsets.UTF_8);
        } catch (FileNotFoundException e) {
            return "default";
        }
    }

    /**
     * Provides the authentication configuration based on in-cluster service account files.
     *
     * @return an {@link AuthConfigProvider} instance that supplies CA certificate and token streams
     */
    private static AuthConfigProvider getAuthConfigProvider() {
        return new AuthConfigProvider() {

            @Override
            public InputStream getCaCert() throws Exception {
                return new FileInputStream(KUBERNETES_SERVICE_ACCOUNT_CA_CERT_PATH);
            }

            @Override
            public InputStream getToken() throws Exception {
                return new FileInputStream(KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH);
            }
        };
    }
}
