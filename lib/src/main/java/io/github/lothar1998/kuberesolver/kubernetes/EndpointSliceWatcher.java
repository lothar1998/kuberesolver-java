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

/**
 * Watches Kubernetes EndpointSlice resource for changes using the Kubernetes Watch API.
 * This class handles streaming events related to a specific service's endpoint slices
 * and notifies a {@link Subscriber} about those changes.
 * <p>
 * Implementations must provide the request and HTTP client logic appropriate for secure or insecure access.
 */
public abstract sealed class EndpointSliceWatcher permits InsecureEndpointSliceWatcher, SecureEndpointSliceWatcher {

    private static final String KUBERNETES_WATCH_ENDPOINT_SLICES_URL_PATTERN = "%s/apis/discovery.k8s.io/v1/watch/namespaces/%s/endpointslices?labelSelector=kubernetes.io/service-name=%s";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);

    private final String host;
    private final String namespace;

    /**
     * Constructs a new watcher for a given Kubernetes API server and namespace.
     *
     * @param host the base URL of the Kubernetes API server
     * @param namespace the Kubernetes namespace to watch for endpoint slices
     */
    public EndpointSliceWatcher(String host, String namespace) {
        this.host = host;
        this.namespace = namespace;
    }

    /**
     * Starts watching for EndpointSlice events associated with a given service name.
     * Events are streamed and passed to the provided subscriber.
     *
     * @param serviceName the name of the Kubernetes service
     * @param subscriber the subscriber that receives events, errors, and completion signals
     * @throws UnexpectedStatusCodeException if the response status from the Kubernetes API is not 200
     */
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

    /**
     * Constructs an HTTP request to watch the EndpointSlices of the given service.
     *
     * @param serviceName the name of the Kubernetes service
     * @return the constructed {@link HttpRequest}
     * @throws Exception if an error occurs while constructing the request
     */
    protected abstract HttpRequest getRequest(String serviceName) throws Exception;

    /**
     * Returns the HTTP client used to make requests to the Kubernetes API.
     *
     * @return an {@link HttpClient} instance
     * @throws Exception if the client cannot be constructed
     */
    protected abstract HttpClient getClient() throws Exception;

    /**
     * Constructs the full URI for watching EndpointSlices of the specified service.
     *
     * @param serviceName the name of the Kubernetes service
     * @return the constructed {@link URI}
     * @throws URISyntaxException if the URI is invalid
     * @throws MalformedURLException if the URL is invalid
     */
    protected URI getURI(String serviceName) throws URISyntaxException, MalformedURLException {
        var url = new URL(String.format(KUBERNETES_WATCH_ENDPOINT_SLICES_URL_PATTERN, host, namespace, serviceName));
        return url.toURI();
    }

    /**
     * Callback interface for receiving streamed EndpointSlice watch events.
     */
    public interface Subscriber {
        /**
         * Called when a new EndpointSlice event is received.
         *
         * @param event the event data
         */
        void onEvent(Event event);

        /**
         * Called when an error occurs during watch processing.
         *
         * @param throwable the exception or error
         */
        void onError(Throwable throwable);

        /**
         * Called when the watch stream completes successfully.
         */
        void onCompleted();
    }

    /**
     * Exception thrown when a non-200 HTTP response is received from the Kubernetes API.
     */
    public static class UnexpectedStatusCodeException extends RuntimeException {
        /**
         * Constructs the exception with a message describing the unexpected status code.
         *
         * @param message the error message
         */
        public UnexpectedStatusCodeException(String message) {
            super(message);
        }
    }
}
