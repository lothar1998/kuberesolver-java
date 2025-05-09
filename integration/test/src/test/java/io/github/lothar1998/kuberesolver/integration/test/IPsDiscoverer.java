package io.github.lothar1998.kuberesolver.integration.test;

import static org.awaitility.Awaitility.await;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class IPsDiscoverer {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient client = HttpClient.newHttpClient();

    private final URI uri;

    public IPsDiscoverer(InetAddress address, int port) {
        String host = address.getHostAddress();

        if (address instanceof Inet6Address) {
            host = "[" + host + "]";
        }

        this.uri = URI.create(String.format("http://%s:%d/ip", host, port));
    }

    public Set<String> getDiscoveredIPs() {
        return await().until(this::fetchDiscoveredIPs, Optional::isPresent).get();
    }

    public void reset() {
        await().until(this::clearDiscoveredIPs);
    }

    private Optional<Set<String>> fetchDiscoveredIPs() {
        var request = HttpRequest.newBuilder()
                .uri(this.uri)
                .GET()
                .build();

        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                return Optional.empty();
            }

            TypeReference<Map<String, Integer>> typeRef = new TypeReference<>() {
            };

            var ips = OBJECT_MAPPER.readValue(response.body(), typeRef)
                    .entrySet()
                    .stream()
                    .filter(this::hasBeenCalledAtLeastOnce)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            return Optional.of(ips);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private boolean clearDiscoveredIPs() {
        var request = HttpRequest.newBuilder()
                .uri(this.uri)
                .DELETE()
                .build();

        try {
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasBeenCalledAtLeastOnce(Map.Entry<String, Integer> ipStat) {
        return ipStat.getValue() > 0;
    }
}
