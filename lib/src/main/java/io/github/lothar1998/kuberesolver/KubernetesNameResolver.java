package io.github.lothar1998.kuberesolver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.github.lothar1998.kuberesolver.kubernetes.EndpointSliceWatcher;
import io.github.lothar1998.kuberesolver.kubernetes.InClusterEndpointSliceWatcher;
import io.github.lothar1998.kuberesolver.kubernetes.model.EndpointPort;
import io.github.lothar1998.kuberesolver.kubernetes.model.EndpointSlice;
import io.github.lothar1998.kuberesolver.kubernetes.model.Event;
import io.github.lothar1998.kuberesolver.kubernetes.model.EventType;

import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.Status;

/**
 * A gRPC {@link NameResolver} implementation that resolves Kubernetes services
 * using EndpointSlices.
 * <p>
 * This resolver watches for changes in Kubernetes EndpointSlices and updates
 * the gRPC client with the resolved addresses.
 * <p>
 * The target URI for this resolver is parsed by {@link ResolverTarget}, which
 * supports the following formats:
 * <ul>
 *   <li>{@code kubernetes:///service-name}</li>
 *   <li>{@code kubernetes:///service-name:8080} (with port number)</li>
 *   <li>{@code kubernetes:///service-name:portname} (with port name)</li>
 *   <li>{@code kubernetes:///service-name.namespace:8080} (with namespace and port number)</li>
 *   <li>{@code kubernetes:///service-name.namespace.svc.cluster_name} (with namespace)</li>
 *   <li>{@code kubernetes:///service-name.namespace.svc.cluster_name:8080} (with namespace and port number)</li>
 *
 *   <li>{@code kubernetes://namespace/service-name:8080}</li>
 *   <li>{@code kubernetes://service-name}</li>
 *   <li>{@code kubernetes://service-name:8080/}</li>
 *   <li>{@code kubernetes://service-name.namespace:8080/}</li>
 *   <li>{@code kubernetes://service-name.namespace.svc.cluster_name}</li>
 *   <li>{@code kubernetes://service-name.namespace.svc.cluster_name:8080}</li>
 * </ul>
 * <p>
 * If the namespace is not provided in the URI, the resolver will attempt to read
 * the current pod's namespace from the mounted file at
 * {@code /var/run/secrets/kubernetes.io/serviceaccount/namespace}. If this file
 * is not found or cannot be read, the resolver will default to using the
 * {@code default} namespace.
 * <p>
 * If the port is not provided in the URI, the resolver will use any of the ports
 * found in the EndpointSlice. If a port name is provided (e.g.,
 * {@code kubernetes:///myservice:grpc}), the resolver will look for a port with
 * that name in the EndpointSlice. If a numerical port is provided, that port
 * will be used directly.
 */
public final class KubernetesNameResolver extends NameResolver {

    private static final Logger LOGGER = Logger.getLogger(KubernetesNameResolver.class.getName());

    private static final Set<EventType> SUPPORTED_KUBERNETES_EVENTS = Set.of(EventType.ADDED, EventType.MODIFIED,
            EventType.DELETED);

    private final Executor executor;
    private final ResolverTarget params;

    private final EndpointSliceWatcher watcher;
    private final Semaphore semaphore = new Semaphore(1);

    private boolean defaultExecutorUsed = false;
    private Listener listener;

    /**
     * Creates a new {@link KubernetesNameResolver} with a default single-threaded
     * executor.
     *
     * @param params the target parameters for the resolver
     * @throws IOException if an error occurs while initializing the watcher
     */
    public KubernetesNameResolver(ResolverTarget params) throws IOException {
        this(Executors.newSingleThreadExecutor(), params);
        this.defaultExecutorUsed = true;
    }

    /**
     * Creates a new {@link KubernetesNameResolver} with a custom executor.
     *
     * @param executor the executor to use for background tasks
     * @param params   the target parameters for the resolver
     * @throws IOException if an error occurs while initializing the watcher
     */
    public KubernetesNameResolver(Executor executor, ResolverTarget params) throws IOException {
        this.executor = executor;
        this.params = params;
        if (params.namespace() != null) {
            this.watcher = new InClusterEndpointSliceWatcher(params.namespace());
        } else {
            this.watcher = new InClusterEndpointSliceWatcher();
        }
    }

    /**
     * Starts the name resolution process.
     *
     * @param listener the listener to notify when addresses are resolved or errors
     *                 occur
     */
    @Override
    public void start(Listener listener) {
        this.listener = listener;
        refresh();
    }

    /**
     * Refreshes the name resolution process. This method is called when the gRPC
     * client requests a refresh.
     */
    @Override
    public void refresh() {
        if (semaphore.tryAcquire()) {
            resolve();
        }
    }

    /**
     * Resolves the Kubernetes service by watching EndpointSlices.
     */
    private void resolve() {
        executor.execute(this::watch);
    }

    /**
     * Watches for changes in EndpointSlices and updates the listener with resolved
     * addresses.
     */
    private void watch() {
        watcher.watch(params.service(), new EndpointSliceWatcher.Subscriber() {
            private final Map<String, List<Set<SocketAddress>>> endpoints = new ConcurrentHashMap<>();

            @Override
            public void onEvent(Event event) {
                // watch event occurred
                if (!SUPPORTED_KUBERNETES_EVENTS.contains(event.type())) {
                    LOGGER.log(Level.FINER, "Unsupported Kubernetes event type {0}",
                            new Object[]{event.type().toString()});
                    return;
                }

                if (event.endpointSlice() == null) {
                    LOGGER.log(Level.FINE, "No EndpointSlice found in watch event");
                    return;
                }

                if (event.endpointSlice().metadata() == null || event.endpointSlice().metadata().name() == null) {
                    LOGGER.log(Level.FINE, "No EndpointSlice name found in watch event metadata");
                    return;
                }

                if (event.type().equals(EventType.DELETED)) {
                    LOGGER.log(Level.FINE, "EndpointSlice {0} was deleted",
                            new Object[]{event.endpointSlice().metadata().name()});
                    endpoints.remove(event.endpointSlice().metadata().name());
                    return;
                }

                LOGGER.log(Level.FINER, "Resolving addresses for service {0}", new Object[]{params.service()});
                var endpointSliceAddresses = buildAddresses(event.endpointSlice());
                if (endpointSliceAddresses.isEmpty()) {
                    LOGGER.log(Level.FINE, "No usable addresses found for service {0} in EndpointSlice {1}",
                            new Object[]{params.service(), event.endpointSlice().metadata().name()});
                } else {
                    LOGGER.log(Level.FINEST,
                            () -> String.format(
                                    "Resolved addresses for service %s from EndpointSlice %s: %s",
                                    params.service(),
                                    event.endpointSlice().metadata().name(),
                                    addressGroupsToString(endpointSliceAddresses.get())
                            ));
                    endpoints.put(event.endpointSlice().metadata().name(), endpointSliceAddresses.get());

                    var allAddresses = endpoints.values().stream()
                            .flatMap(List::stream)
                            .distinct()
                            .toList();

                    LOGGER.log(Level.FINEST, () -> String.format(
                            "All resolved addresses for service %s: %s",
                            params.service(), addressGroupsToString(allAddresses)));
                    listener.onAddresses(toEquivalentAddressGroups(allAddresses), Attributes.EMPTY);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                // watch encountered an error
                LOGGER.log(Level.FINE, "Encountered an error when watching EndpointSlice", throwable);
                listener.onError(Status.fromThrowable(throwable));
                semaphore.release();
            }

            @Override
            public void onCompleted() {
                // watch was finished and it should be performed again after some backoff
                LOGGER.log(Level.FINER, "Watch stream of EndpointSlice was finished by server");
                listener.onError(Status.UNAVAILABLE);
                semaphore.release();
            }
        });
    }

    /**
     * Shuts down the resolver and releases resources.
     */
    @Override
    public void shutdown() {
        if (defaultExecutorUsed && executor instanceof ExecutorService executor) {
            executor.shutdownNow();
        }
    }

    /**
     * Returns the authority of the service being resolved.
     *
     * @return an empty string as this resolver does not use service authority
     */
    @Override
    public String getServiceAuthority() {
        return "";
    }

    /**
     * Extracts and processes network addresses from a Kubernetes {@link EndpointSlice}.
     * <p>
     * This method performs several key steps in the address resolution process:
     * <ol>
     *   <li>Finds the appropriate port to use from the EndpointSlice</li>
     *   <li>Filters for endpoints that are in the "ready" condition</li>
     *   <li>Maps each endpoint's IP addresses to socket addresses using the resolved port</li>
     * </ol>
     * <p>
     * If no suitable port can be found or if the EndpointSlice contains no ready endpoints,
     * an empty Optional will be returned.
     *
     * @param endpointSlice the Kubernetes EndpointSlice containing endpoint information
     * @return an Optional containing a list of socket address sets for ready endpoints,
     * or an empty Optional if no addresses could be resolved
     */
    private Optional<List<Set<SocketAddress>>> buildAddresses(EndpointSlice endpointSlice) {
        return findPort(endpointSlice.ports())
                .map(port -> endpointSlice.endpoints().stream()
                        .filter(endpoint -> endpoint.conditions().isReady())
                        .map(endpoint -> buildAddressGroup(endpoint.addresses(), port))
                        .filter(group -> !group.isEmpty())
                        .toList());
    }

    /**
     * Finds the port to use for the service from the list of ports in the
     * EndpointSlice. If the port is not provided in {@link ResolverTarget}
     * then first port found in EndpointSlice is used.
     *
     * @param ports the list of ports in the EndpointSlice
     * @return an optional port number
     */
    private Optional<Integer> findPort(List<EndpointPort> ports) {
        if (params.port() == null) {
            return ports.stream().map(EndpointPort::port).findFirst();
        }

        try {
            return Optional.of(Integer.parseInt(params.port()));
        } catch (NumberFormatException e) {
            return ports.stream()
                    .filter(port -> port.name().equals(params.port()))
                    .map(EndpointPort::port)
                    .findFirst();
        }
    }

    /**
     * Builds a set of socket addresses from a list of IP addresses and a port number.
     * This method converts each IP address into an {@link InetSocketAddress} using the given port,
     * which represents one endpoint in a Kubernetes EndpointSlice.
     * <p>
     * The resulting set of addresses is used in the name resolution process to provide
     * gRPC clients with possible connection endpoints for the target service.
     *
     * @param addresses the list of IP addresses from a Kubernetes endpoint
     * @param port      the port number to use for all addresses
     * @return a set of {@link SocketAddress} objects representing the endpoint addresses
     */
    private Set<SocketAddress> buildAddressGroup(List<String> addresses, int port) {
        return addresses.stream()
                .map(address -> (SocketAddress) new InetSocketAddress(address, port))
                .collect(Collectors.toSet());
    }

    /**
     * Converts a list of socket address sets into a list of {@link EquivalentAddressGroup} objects.
     * Each set of socket addresses is transformed into a single {@link EquivalentAddressGroup},
     * which gRPC uses to represent a group of equivalent addresses for load balancing.
     *
     * @param addressGroups the list of socket address sets to convert
     * @return a list of {@link EquivalentAddressGroup} objects, each representing one set of addresses
     */
    private List<EquivalentAddressGroup> toEquivalentAddressGroups(List<Set<SocketAddress>> addressGroups) {
        return addressGroups.stream()
                .map(group -> new EquivalentAddressGroup(new ArrayList<>(group)))
                .toList();
    }

    /**
     * Converts a list of socket address sets into a human-readable string representation.
     * The format is a nested structure like: [(addr1, addr2), (addr3), (addr4, addr5)]
     * where each set of addresses is represented as a group in parentheses.
     *
     * @param addressGroups the list of socket address sets to convert to string
     * @return a string representation of the address groups
     */
    private String addressGroupsToString(List<Set<SocketAddress>> addressGroups) {
        if (addressGroups == null || addressGroups.isEmpty()) {
            return "[]";
        }

        var result = new StringBuilder("[");

        result.append("(");
        boolean firstAddr = true;
        for (SocketAddress address : addressGroups.get(0)) {
            if (!firstAddr) {
                result.append(", ");
            }
            result.append(address);
            firstAddr = false;
        }
        result.append(")");

        for (int i = 1; i < addressGroups.size(); i++) {
            result.append(", (");
            firstAddr = true;
            for (SocketAddress address : addressGroups.get(i)) {
                if (!firstAddr) {
                    result.append(", ");
                }
                result.append(address);
                firstAddr = false;
            }
            result.append(")");
        }

        result.append("]");
        return result.toString();
    }
}
