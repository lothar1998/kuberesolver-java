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

public sealed class SecureEndpointSliceWatcher extends EndpointSliceWatcher permits InClusterEndpointSliceWatcher {

    private final AuthConfigProvider authConfig;

    public SecureEndpointSliceWatcher(String host, String namespace, AuthConfigProvider authConfig) {
        super(host, namespace);
        this.authConfig = authConfig;
    }

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

    @Override
    protected HttpRequest getRequest(String serviceName) throws Exception {
        return HttpRequest.newBuilder(getURI(serviceName))
                .GET()
                .setHeader("Authorization", String.format("Bearer %s", getToken()))
                .setHeader("Accept", "application/json")
                .build();
    }

    private String getToken() throws Exception {
        try (var token = authConfig.getToken()) {
            var bytes = token.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    public interface AuthConfigProvider {
        InputStream getCaCert() throws Exception;

        InputStream getToken() throws Exception;
    }
}
