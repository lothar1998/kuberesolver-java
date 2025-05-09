package io.github.lothar1998.kuberesolver.kubernetes;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lothar1998.kuberesolver.kubernetes.EndpointSliceWatcher.UnexpectedStatusCodeException;
import io.github.lothar1998.kuberesolver.kubernetes.model.Conditions;
import io.github.lothar1998.kuberesolver.kubernetes.model.Endpoint;
import io.github.lothar1998.kuberesolver.kubernetes.model.EndpointPort;
import io.github.lothar1998.kuberesolver.kubernetes.model.EndpointSlice;
import io.github.lothar1998.kuberesolver.kubernetes.model.Event;
import io.github.lothar1998.kuberesolver.kubernetes.model.EventType;
import io.github.lothar1998.kuberesolver.kubernetes.model.Metadata;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

@WireMockTest
class InsecureEndpointSliceWatcherTest {

    private static final String PATH_TEMPLATE = "/apis/discovery.k8s.io/v1/watch/namespaces/{namespace}/endpointslices";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @DisplayName("should watch on events from HTTP server until the server ends the stream")
    @Test
    void watchAllStreamedEventsTest(WireMockRuntimeInfo wmRuntimeInfo) {
        var event1 = new Event(
                EventType.ADDED,
                new EndpointSlice(
                        new Metadata("my-service-endpoint-slice"),
                        List.of(new Endpoint(List.of("10.0.0.1", "10.0.1.1"), new Conditions(true))),
                        List.of(new EndpointPort(null, 8080))));

        var event2 = new Event(
                EventType.MODIFIED,
                new EndpointSlice(
                        new Metadata("my-service-endpoint-slice"),
                        List.of(
                                new Endpoint(List.of("10.0.0.1", "10.0.1.1"), new Conditions(true)),
                                new Endpoint(List.of("10.0.0.2", "10.0.1.2"), new Conditions(false))),
                        List.of(new EndpointPort("port-name", 8080))));

        var event3 = new Event(
                EventType.DELETED,
                new EndpointSlice(
                        new Metadata("my-service-endpoint-slice"),
                        List.of(
                                new Endpoint(List.of("10.0.0.1", "10.0.1.1"), new Conditions(true)),
                                new Endpoint(List.of("10.0.0.2", "10.0.1.2"), new Conditions(false))),
                        List.of(new EndpointPort("port-name", 8080))));

        var chunkedBody = Stream.of(event1, event2, event3)
                .map(event -> {
                    try {
                        return OBJECT_MAPPER.writeValueAsString(event);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.joining("\n"));

        stubFor(
                get(urlPathTemplate(PATH_TEMPLATE))
                        .withPathParam("namespace", equalTo("my-namespace"))
                        .withQueryParam("labelSelector", equalTo("kubernetes.io/service-name=my-service"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(ok(chunkedBody)
                                .withHeader("Content-Type", "application/json")
                                .withChunkedDribbleDelay(3, 1)));

        var watcher = new InsecureEndpointSliceWatcher(wmRuntimeInfo.getHttpBaseUrl(), "my-namespace");
        var subscriber = mock(InsecureEndpointSliceWatcher.Subscriber.class);
        watcher.watch("my-service", subscriber);

        var inOrder = inOrder(subscriber);
        inOrder.verify(subscriber).onEvent(event1);
        inOrder.verify(subscriber).onEvent(event2);
        inOrder.verify(subscriber).onEvent(event3);
        inOrder.verify(subscriber).onCompleted();
    }

    @DisplayName("should fail watching events due to invalid url")
    @Test
    void watchFailsDueToInvalidURL() {
        var watcher = new InsecureEndpointSliceWatcher("invalid_path", "default");
        var subscriber = mock(InsecureEndpointSliceWatcher.Subscriber.class);
        watcher.watch("service", subscriber);

        var captor = ArgumentCaptor.forClass(Throwable.class);
        verify(subscriber).onError(captor.capture());
        verify(subscriber, never()).onEvent(any());
        verify(subscriber, never()).onCompleted();

        assertInstanceOf(MalformedURLException.class, captor.getValue());
    }

    @DisplayName("should fail watching events due to invalid uri")
    @Test
    void watchFailsDueToInvalidURI() {
        var watcher = new InsecureEndpointSliceWatcher("http://invalid path", "default");
        var subscriber = mock(InsecureEndpointSliceWatcher.Subscriber.class);
        watcher.watch("service", subscriber);

        var captor = ArgumentCaptor.forClass(Throwable.class);
        verify(subscriber).onError(captor.capture());
        verify(subscriber, never()).onEvent(any());
        verify(subscriber, never()).onCompleted();

        assertInstanceOf(URISyntaxException.class, captor.getValue());
    }

    @DisplayName("should fail watching events due to non ok HTTP status code")
    @Test
    void watchFailsDueToNotOkHTTPStatusCode(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(
                get(urlPathTemplate(PATH_TEMPLATE))
                        .withPathParam("namespace", equalTo("my-namespace"))
                        .withQueryParam("labelSelector", equalTo("kubernetes.io/service-name=my-service"))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(status(500)));

        var watcher = new InsecureEndpointSliceWatcher(wmRuntimeInfo.getHttpBaseUrl(), "my-namespace");
        var subscriber = mock(InsecureEndpointSliceWatcher.Subscriber.class);
        watcher.watch("my-service", subscriber);

        var captor = ArgumentCaptor.forClass(Throwable.class);
        verify(subscriber).onError(captor.capture());
        verify(subscriber, never()).onEvent(any());
        verify(subscriber, never()).onCompleted();

        assertInstanceOf(UnexpectedStatusCodeException.class, captor.getValue());
    }
}
