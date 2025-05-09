package io.github.lothar1998.kuberesolver;

import java.net.URI;
import javax.annotation.Nullable;
import javax.annotation.Nonnull;

public record ResolverTarget(@Nullable String namespace,
                             @Nonnull String service,
                             @Nullable String port) {

    public static ResolverTarget parse(URI uri) throws IllegalArgumentException {
        ResolverTarget params;
        if (uri.getAuthority() == null || uri.getAuthority().isEmpty()) {
            // kubernetes:///service.namespace:port
            params = parse(trimLeadingSlash(uri.getPath()));
        } else if (uri.getPort() == -1 && (uri.getPath() != null && !uri.getPath().isEmpty())) {
            // kubernetes://namespace/service:port
            params = parse(trimLeadingSlash(uri.getPath()));
            params = new ResolverTarget(uri.getAuthority(), params.service, params.port);
        } else {
            // kubernetes://service.namespace:port
            params = parse(uri.getAuthority());
        }

        if (params.service.isEmpty()) {
            throw new IllegalArgumentException(String.format("cannot parse service name from URI '%s'", uri));
        }
        return params;
    }

    private static ResolverTarget parse(String s) {
        String service = s;
        String namespace = null;
        String port = null;

        int colonIndex = s.lastIndexOf(':');
        if (colonIndex != -1) {
            service = s.substring(0, colonIndex);
            port = s.substring(colonIndex + 1);
        }

        var parts = service.split("\\.", 3);
        if (parts.length >= 2) {
            service = parts[0];
            namespace = parts[1];
        }

        return new ResolverTarget(namespace, service, port);
    }

    private static String trimLeadingSlash(String s) {
        if (s.startsWith("/")) {
            return s.substring(1);
        }
        return s;
    }
}
