package io.github.lothar1998.kuberesolver.kubernetes;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

public final class InClusterEndpointSliceWatcher extends SecureEndpointSliceWatcher {

    private static final String KUBERNETES_SERVICE_HOST = "KUBERNETES_SERVICE_HOST";
    private static final String KUBERNETES_SERVICE_PORT = "KUBERNETES_SERVICE_PORT";

    private static final String KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token";
    private static final String KUBERNETES_SERVICE_ACCOUNT_CA_CERT_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";
    private static final String KUBERNETES_NAMESPACE_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/namespace";

    public InClusterEndpointSliceWatcher() throws IOException {
        this(getNamespace());
    }

    public InClusterEndpointSliceWatcher(String namespace) {
        super(getHost(), namespace, getAuthConfigProvider());
    }

    private static String getHost() {
        var address = Optional.of(System.getenv(KUBERNETES_SERVICE_HOST)).orElseThrow(
                () -> new RuntimeException(String.format("%s env variable not set", KUBERNETES_SERVICE_HOST)));

        var port = Optional.of(System.getenv(KUBERNETES_SERVICE_PORT)).orElseThrow(
                () -> new RuntimeException(String.format("%s env variable not set", KUBERNETES_SERVICE_PORT)));

        return String.format("https://%s:%s", address, port);
    }

    private static String getNamespace() throws IOException {
        try {
            return Files.readString(Paths.get(KUBERNETES_NAMESPACE_PATH), StandardCharsets.UTF_8);
        } catch (FileNotFoundException e) {
            return "default";
        }
    }

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
