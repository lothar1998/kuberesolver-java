package io.github.lothar1998.kuberesolver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

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
 * </p>
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
 * </p>
 * <p>
 * If the namespace is not provided in the URI, the resolver will attempt to read
 * the current pod's namespace from the mounted file at
 * {@code /var/run/secrets/kubernetes.io/serviceaccount/namespace}. If this file
 * is not found or cannot be read, the resolver will default to using the
 * {@code default} namespace.
 * </p>
 * <p>
 * If the port is not provided in the URI, the resolver will use any of the ports
 * found in the EndpointSlice. If a port name is provided (e.g.,
 * {@code kubernetes:///myservice:grpc}), the resolver will look for a port with
 * that name in the EndpointSlice. If a numerical port is provided, that port
 * will be used directly.
 * </p>
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
        resolve();
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
            @Override
            public void onEvent(Event event) {
                // watch event occurred
                if (!SUPPORTED_KUBERNETES_EVENTS.contains(event.type())) {
                    LOGGER.log(Level.FINER, "Unsupported Kubernetes event type {0}",
                            new Object[]{event.type().toString()});
                    return;
                }

                if (event.type().equals(EventType.DELETED)) {
                    LOGGER.log(Level.FINE, "EndpointSlice {0} was deleted",
                            new Object[]{event.endpointSlice().metadata().name()});
                    return;
                }

                if (event.endpointSlice() == null) {
                    LOGGER.log(Level.FINE, "No EndpointSlice found in watch event");
                    return;
                }

                LOGGER.log(Level.FINER, "Resolving addresses for service {0}", new Object[]{params.service()});
                buildAddresses(event.endpointSlice()).ifPresentOrElse(a -> listener.onAddresses(a, Attributes.EMPTY),
                        () -> LOGGER.log(Level.FINE, "No usable addresses found for Kubernetes service {0}",
                                new Object[]{params.service()}));
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
     * Builds a list of gRPC {@link EquivalentAddressGroup} from the given
     * {@link EndpointSlice}.
     *
     * @param endpointSlice the EndpointSlice to process
     * @return an optional list of resolved addresses
     */
    private Optional<List<EquivalentAddressGroup>> buildAddresses(EndpointSlice endpointSlice) {
        return findPort(endpointSlice.ports())
                .map(port -> endpointSlice.endpoints().stream()
                        .filter(endpoint -> endpoint.conditions().isReady())
                        .map(endpoint -> buildAddressGroup(endpoint.addresses(), port))
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
     * Builds a gRPC {@link EquivalentAddressGroup} from the given addresses and
     * port.
     *
     * @param addresses the list of addresses
     * @param port      the port number
     * @return an {@link EquivalentAddressGroup} containing the resolved addresses
     */
    private EquivalentAddressGroup buildAddressGroup(List<String> addresses, int port) {
        var socketAddresses = addresses.stream()
                .map(address -> (SocketAddress) new InetSocketAddress(address, port))
                .toList();
        return new EquivalentAddressGroup(socketAddresses, Attributes.EMPTY);
    }
}
