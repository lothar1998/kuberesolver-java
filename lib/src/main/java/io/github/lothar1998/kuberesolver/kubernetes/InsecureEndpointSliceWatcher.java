package io.github.lothar1998.kuberesolver.kubernetes;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpClient.Version;

public final class InsecureEndpointSliceWatcher extends EndpointSliceWatcher {

    public InsecureEndpointSliceWatcher(String host, String namespace) {
        super(host, namespace);
    }

    @Override
    protected HttpClient getClient() {
        return HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .build();
    }

    @Override
    protected HttpRequest getRequest(String serviceName) throws Exception {
        return HttpRequest.newBuilder(getURI(serviceName))
                .setHeader("Accept", "application/json")
                .build();
    }

}
