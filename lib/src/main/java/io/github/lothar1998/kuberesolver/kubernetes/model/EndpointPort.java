package io.github.lothar1998.kuberesolver.kubernetes.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a port associated with a Kubernetes endpoint.
 *
 * @param name the name of the port
 * @param port the numerical port value
 */
public record EndpointPort(@JsonProperty("name") String name, @JsonProperty("port") int port) {
}
