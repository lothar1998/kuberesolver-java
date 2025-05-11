package io.github.lothar1998.kuberesolver.kubernetes.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * Represents a Kubernetes EndpointSlice object, which provides a scalable and extensible mechanism
 * for tracking network endpoints in a Kubernetes cluster.
 *
 * @param metadata  metadata associated with the EndpointSlice, including its name
 * @param endpoints a list of endpoints representing network addresses and associated conditions
 * @param ports     a list of ports associated with the endpoints
 */
public record EndpointSlice(
        @JsonProperty("metadata") Metadata metadata,
        @JsonProperty("endpoints") @JsonSetter(nulls = Nulls.AS_EMPTY) List<Endpoint> endpoints,
        @JsonProperty("ports") @JsonSetter(nulls = Nulls.AS_EMPTY) List<EndpointPort> ports) {
}
