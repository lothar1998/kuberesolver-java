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

    public KubernetesNameResolver(ResolverTarget params) throws IOException {
        this(Executors.newSingleThreadExecutor(), params);
        this.defaultExecutorUsed = true;
    }

    public KubernetesNameResolver(Executor executor, ResolverTarget params)
            throws IOException {
        this.executor = executor;
        this.params = params;
        if (params.namespace() != null) {
            this.watcher = new InClusterEndpointSliceWatcher(params.namespace());
        } else {
            this.watcher = new InClusterEndpointSliceWatcher();
        }
    }

    @Override
    public void start(Listener listener) {
        this.listener = listener;
        resolve();
    }

    @Override
    public void refresh() {
        if (semaphore.tryAcquire()) {
            resolve();
        }
    }

    private void resolve() {
        executor.execute(this::watch);
    }

    private void watch() {
        watcher.watch(params.service(), new EndpointSliceWatcher.Subscriber() {
            @Override
            public void onEvent(Event event) {
                // watch event occurred
                if (!SUPPORTED_KUBERNETES_EVENTS.contains(event.type())) {
                    LOGGER.log(Level.FINER, "Unsupported Kubernetes event type {0}",
                            new Object[] { event.type().toString() });
                    return;
                }

                if (event.type().equals(EventType.DELETED)) {
                    LOGGER.log(Level.FINE, "EndpointSlice {0} was deleted",
                            new Object[] { event.endpointSlice().metadata().name() });
                    return;
                }

                if (event.endpointSlice() == null) {
                    LOGGER.log(Level.FINE, "No EndpointSlice found in watch event");
                    return;
                }

                LOGGER.log(Level.FINER, "Resolving addresses for service {0}", new Object[] { params.service() });
                buildAddresses(event.endpointSlice()).ifPresentOrElse(a -> listener.onAddresses(a, Attributes.EMPTY),
                        () -> LOGGER.log(Level.FINE, "No usable addresses found for Kubernetes service {0}",
                                new Object[] { params.service() }));
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

    @Override
    public void shutdown() {
        if (defaultExecutorUsed && executor instanceof ExecutorService executor) {
            executor.shutdownNow();
        }
    }

    @Override
    public String getServiceAuthority() {
        return "";
    }

    private Optional<List<EquivalentAddressGroup>> buildAddresses(EndpointSlice endpointSlice) {
        return findPort(endpointSlice.ports())
                .map(port -> endpointSlice.endpoints().stream()
                        .filter(endpoint -> endpoint.conditions().isReady())
                        .map(endpoint -> buildAddressGroup(endpoint.addresses(), port))
                        .toList());
    }

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

    private EquivalentAddressGroup buildAddressGroup(List<String> addresses, int port) {
        var socketAddresses = addresses.stream()
                .map(address -> (SocketAddress) new InetSocketAddress(address, port))
                .toList();
        return new EquivalentAddressGroup(socketAddresses, Attributes.EMPTY);
    }
}
