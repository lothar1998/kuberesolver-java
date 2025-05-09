package io.github.lothar1998.kuberesolver.kubernetes.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EndpointPort(@JsonProperty("name") String name, @JsonProperty("port") int port) {
};
