package io.github.lothar1998.kuberesolver;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Executor;

import io.grpc.NameResolver;
import io.grpc.NameResolver.Args;
import io.grpc.NameResolverProvider;

/**
 * A gRPC {@link NameResolverProvider} that resolves service names using Kubernetes.
 * It supports URIs with the "kubernetes" scheme and returns a {@link KubernetesNameResolver}.
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
