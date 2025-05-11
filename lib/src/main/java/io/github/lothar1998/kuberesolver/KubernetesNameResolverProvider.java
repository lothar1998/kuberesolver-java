package io.github.lothar1998.kuberesolver;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Executor;

import io.grpc.NameResolver;
import io.grpc.NameResolver.Args;
import io.grpc.NameResolverProvider;

/**
 * A gRPC {@link NameResolverProvider} that resolves service names using Kubernetes.
 * <p>
 * This provider supports target URIs as defined by {@link ResolverTarget}, including:
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
 * If the namespace is not explicitly provided in the URI, the underlying
 * {@link KubernetesNameResolver} will first attempt to read the current pod's
 * namespace from the mounted file at
 * {@code /var/run/secrets/kubernetes.io/serviceaccount/namespace}. If this file
 * is not found or cannot be read, it will default to using the {@code default}
 * namespace.
 * </p>
 * <p>
 * If the port is not specified in the URI, the resolver will use any of the ports
 * defined in the Kubernetes EndpointSlice. Alternatively, a port can be specified
 * by its name in the URI (e.g., {@code kubernetes:///myservice:grpc}), in which
 * case the resolver will look for an EndpointSlice port with that name. If a
 * numerical port is provided, that port will be used.
 * </p>
 */
public class KubernetesNameResolverProvider extends NameResolverProvider {

    private String scheme = "kubernetes";

    /**
     * Constructs a new provider with a custom scheme.
     *
     * @param schema the URI scheme this provider should support (e.g., "kubernetes")
     */
    public KubernetesNameResolverProvider(String schema) {
        this.scheme = schema;
    }

    /**
     * Constructs a new provider with the default scheme ("kubernetes").
     */
    public KubernetesNameResolverProvider() {
    }

    /**
     * Indicates whether this provider is available for use.
     *
     * @return always returns {@code true}
     */
    @Override
    protected boolean isAvailable() {
        return true;
    }

    /**
     * Returns the priority of this provider.
     *
     * @return the priority value (5)
     */
    @Override
    protected int priority() {
        return 5;
    }

    /**
     * Returns a new {@link NameResolver} for the given target URI and arguments, if the URI scheme matches.
     *
     * @param targetUri the URI to resolve
     * @param args      the resolver arguments
     * @return a new {@link KubernetesNameResolver}, or {@code null} if the scheme does not match
     */
    @Override
    public NameResolver newNameResolver(URI targetUri, Args args) {
        if (targetUri.getScheme().equals(this.scheme)) {
            var params = ResolverTarget.parse(targetUri);
            return buildResolver(args.getOffloadExecutor(), params);
        }
        return null;
    }

    /**
     * Builds a {@link KubernetesNameResolver} using the provided executor and target parameters.
     *
     * @param executor the executor for offloading tasks
     * @param params   the parsed target parameters
     * @return a new {@link KubernetesNameResolver}
     * @throws RuntimeException if an I/O error occurs
     */
    private NameResolver buildResolver(Executor executor, ResolverTarget params) {
        try {
            if (executor != null) {
                return new KubernetesNameResolver(executor, params);
            }
            return new KubernetesNameResolver(params);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the default scheme supported by this provider.
     *
     * @return the default scheme
     */
    @Override
    public String getDefaultScheme() {
        return this.scheme;
    }
}
