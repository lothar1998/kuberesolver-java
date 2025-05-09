package io.github.lothar1998.kuberesolver.kubernetes.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

public record EndpointSlice(
                @JsonProperty("metadata") Metadata metadata,
                @JsonProperty("endpoints") @JsonSetter(nulls = Nulls.AS_EMPTY) List<Endpoint> endpoints,
                @JsonProperty("ports") @JsonSetter(nulls = Nulls.AS_EMPTY) List<EndpointPort> ports) {
}
