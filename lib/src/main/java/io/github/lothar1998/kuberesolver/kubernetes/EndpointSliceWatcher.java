package io.github.lothar1998.kuberesolver.kubernetes;

import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lothar1998.kuberesolver.kubernetes.model.Event;

public abstract sealed class EndpointSliceWatcher permits InsecureEndpointSliceWatcher, SecureEndpointSliceWatcher {

    private static final String KUBERNETES_WATCH_ENDPOINT_SLICES_URL_PATTERN = "%s/apis/discovery.k8s.io/v1/watch/namespaces/%s/endpointslices?labelSelector=kubernetes.io/service-name=%s";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);

    private final String host;
    private final String namespace;

    public EndpointSliceWatcher(String host, String namespace) {
        this.host = host;
        this.namespace = namespace;
    }

    public void watch(String serviceName, Subscriber subscriber) throws UnexpectedStatusCodeException {
        try {
            var request = getRequest(serviceName);
            var response = getClient().send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new UnexpectedStatusCodeException(
                        String.format("Got HTTP %s status code in response from kube-apiserver",
                                response.statusCode()));
            }

            var responseBody = response.body();

            try (Scanner scanner = new Scanner(new InputStreamReader(responseBody))) {
                while (scanner.hasNextLine()) {
                    var line = scanner.nextLine();
                    var event = OBJECT_MAPPER.readValue(line, Event.class);
                    subscriber.onEvent(event);
                }
            }
            subscriber.onCompleted();
        } catch (Exception e) {
            subscriber.onError(e);
        }
    }

    protected abstract HttpRequest getRequest(String serviceName) throws Exception;

    protected abstract HttpClient getClient() throws Exception;

    protected URI getURI(String serviceName) throws URISyntaxException, MalformedURLException {
        var url = new URL(String.format(KUBERNETES_WATCH_ENDPOINT_SLICES_URL_PATTERN, host, namespace, serviceName));
        return url.toURI();
    }

    public interface Subscriber {
        void onEvent(Event event);

        void onError(Throwable throwable);

        void onCompleted();
    }

    public static class UnexpectedStatusCodeException extends RuntimeException {
        public UnexpectedStatusCodeException(String message) {
            super(message);
        }
    }
}
