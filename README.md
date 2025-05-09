# kuberesolver-java
A gRPC name resolver that uses a Kubernetes API to discover backend servers via `Service` name. This library uses
only the JSON parsing library as the dependency to do not blow your dependency tree.

## Usage
```java
import io.github.lothar1998.kuberesolver.KubernetesNameResolverProvider;
import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolverRegistry;

public class App {
    public static void main(String[] args) {
        // register global Kubernetes name resolver provider
        NameResolverRegistry.getDefaultRegistry().register(new KubernetesNameResolverProvider());

        // build gRPC channel as usual
        var channel = ManagedChannelBuilder
                .forTarget("kubernetes:///service-name:port")
                .build();
    }
}
```

This library supports all targets from the below list:
```
kubernetes:///service-name:8080
kubernetes:///service-name:portname
kubernetes:///service-name.namespace:8080
kubernetes:///service-name.namespace.svc.cluster_name
kubernetes:///service-name.namespace.svc.cluster_name:8080

kubernetes://namespace/service-name:8080
kubernetes://service-name:8080/
kubernetes://service-name.namespace:8080/
kubernetes://service-name.namespace.svc.cluster_name
kubernetes://service-name.namespace.svc.cluster_name:8080
```

## Alternative scheme 
You can use alternative schema (other than `kubernetes`) by using overloaded constructor
`new KubernetesNameResolverProvider("my-custom-scheme")`.

## RBAC
If you are using RBAC in you Kubernetes cluster, you have to give `GET` and `WATCH` access to `endpointslices` resource 
 to allow the resolver to discover the backend servers.

## Acknowledgements

This project is inspired by https://github.com/sercand/kuberesolver.
Special thanks to the authors for their foundational work and design ideas.
