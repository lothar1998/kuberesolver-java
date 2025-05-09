package io.github.lothar1998.kuberesolver.kubernetes.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Event(@JsonProperty("type") EventType type, @JsonProperty("object") EndpointSlice endpointSlice) {
}
