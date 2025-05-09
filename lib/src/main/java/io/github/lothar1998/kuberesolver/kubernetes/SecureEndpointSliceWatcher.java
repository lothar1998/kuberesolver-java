package io.github.lothar1998.kuberesolver.kubernetes;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpRequest;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * A secure implementation of {@link EndpointSliceWatcher} that uses TLS and token-based authentication
 * to communicate with the Kubernetes API server.
 *
 * This watcher sets up a custom {@link SSLContext} with a provided CA certificate and uses a bearer token
 * for authorization headers. It is suitable for production environments requiring secure communication.
 */
public sealed class SecureEndpointSliceWatcher extends EndpointSliceWatcher permits InClusterEndpointSliceWatcher {

    private final AuthConfigProvider authConfig;

    /**
     * Constructs a SecureEndpointSliceWatcher with the specified Kubernetes API host, namespace, and authentication configuration.
     *
     * @param host the Kubernetes API host
     * @param namespace the namespace to watch for EndpointSlices
     * @param authConfig the provider for CA certificate and token used for authentication
     */
    public SecureEndpointSliceWatcher(String host, String namespace, AuthConfigProvider authConfig) {
        super(host, namespace);
        this.authConfig = authConfig;
    }

    /**
     * Creates an {@link HttpClient} configured with a custom {@link SSLContext} that uses the CA certificate
     * provided by the {@link AuthConfigProvider}.
     *
     * @return an HTTP client configured for secure communication with the Kubernetes API
     * @throws Exception if SSL context setup fails
     */
    @Override
    protected HttpClient getClient() throws Exception {
        var cf = CertificateFactory.getInstance("X.509");

        X509Certificate x509Cert;
        try (var inputStream = authConfig.getCaCert()) {
            x509Cert = (X509Certificate) cf.generateCertificate(inputStream);
        }

        var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null);
        keyStore.setCertificateEntry("caCert", x509Cert);

        var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        return HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .sslContext(sslContext)
                .build();
    }

    /**
     * Builds a secure HTTP GET request with authorization and content-type headers to retrieve
     * EndpointSlice information for a given service.
     *
     * @param serviceName the name of the Kubernetes service
     * @return a configured {@link HttpRequest} instance
     * @throws Exception if the request setup fails
     */
    @Override
    protected HttpRequest getRequest(String serviceName) throws Exception {
        return HttpRequest.newBuilder(getURI(serviceName))
                .GET()
                .setHeader("Authorization", String.format("Bearer %s", getToken()))
                .setHeader("Accept", "application/json")
                .build();
    }

    /**
     * Reads the bearer token from the input stream provided by the {@link AuthConfigProvider}.
     *
     * @return the token as a string
     * @throws Exception if reading the token fails
     */
    private String getToken() throws Exception {
        try (var token = authConfig.getToken()) {
            var bytes = token.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * Provider interface for authentication configuration including CA certificate and token stream.
     */
    public interface AuthConfigProvider {
        InputStream getCaCert() throws Exception;

        InputStream getToken() throws Exception;
    }
}
