# kuberesolver-java
A gRPC name resolver that uses a Kubernetes API to discover backend servers via `Service` name. This library uses
only the JSON parsing library as the dependency to do not blow your dependency tree.

### Usage
#### Maven
```xml
<dependency>
    <groupId>io.github.lothar1998</groupId>
    <artifactId>kuberesolver-java</artifactId>
    <version>0.0.2</version>
</dependency>
```

#### Gradle
```groovy
implementation 'io.github.lothar1998:kuberesolver-java:0.0.2'
```
Then register `KubernetesNameResolverProvider` in default registry:
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

### Supported targets
This library supports all targets from the below list:
```
kubernetes:///service-name
kubernetes:///service-name:8080
kubernetes:///service-name:portname
kubernetes:///service-name.namespace:8080
kubernetes:///service-name.namespace.svc.cluster_name
kubernetes:///service-name.namespace.svc.cluster_name:8080

kubernetes://namespace/service-name:8080
kubernetes://service-name
kubernetes://service-name:8080/
kubernetes://service-name.namespace:8080/
kubernetes://service-name.namespace.svc.cluster_name
kubernetes://service-name.namespace.svc.cluster_name:8080
```

#### Namespace handling
If the namespace is not explicitly provided in the target URI, the resolver will first attempt to read the current pod's namespace from the mounted file at `/var/run/secrets/kubernetes.io/serviceaccount/namespace`. If this file is not found or cannot be read, it will default to using the `default` namespace.

#### Port handling
If the port is not specified in the URI, the resolver will use any of the ports defined in the Kubernetes `EndpointSlice`. Alternatively, a port can be specified by its name in the URI (e.g., `kubernetes:///myservice:grpc`), in which case the resolver will look for an `EndpointSlice` port with that name. If a numerical port is provided, that port will be used.

### Alternative scheme 
You can use alternative schema (other than `kubernetes`) by using overloaded constructor:
```new KubernetesNameResolverProvider("my-custom-scheme")```.

### RBAC
If you are using RBAC in you Kubernetes cluster, you have to give `WATCH` access to `endpointslices` resource 
 to allow the resolver to discover the backend servers.

### Acknowledgements

This project is inspired by https://github.com/sercand/kuberesolver.
Special thanks to the authors for their foundational work and design ideas.
