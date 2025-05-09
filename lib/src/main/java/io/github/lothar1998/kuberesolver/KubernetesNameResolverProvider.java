package io.github.lothar1998.kuberesolver;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Executor;

import io.grpc.NameResolver;
import io.grpc.NameResolver.Args;
import io.grpc.NameResolverProvider;

public class KubernetesNameResolverProvider extends NameResolverProvider {

    private String scheme = "kubernetes";

    public KubernetesNameResolverProvider(String schema) {
        this.scheme = schema;
    }

    public KubernetesNameResolverProvider() {
    }

    @Override
    protected boolean isAvailable() {
        return true;
    }

    @Override
    protected int priority() {
        return 5;
    }

    @Override
    public NameResolver newNameResolver(URI targetUri, Args args) {
        if (targetUri.getScheme().equals(this.scheme)) {
            var params = ResolverTarget.parse(targetUri);
            return buildResolver(args.getOffloadExecutor(), params);
        }
        return null;
    }

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

    @Override
    public String getDefaultScheme() {
        return this.scheme;
    }
}
