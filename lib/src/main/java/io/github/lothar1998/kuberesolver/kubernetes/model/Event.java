package io.github.lothar1998.kuberesolver.kubernetes.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a Kubernetes watch event, received from the Kubernetes API server
 * when observing changes to EndpointSlices.
 *
 * @param type          the type of event (e.g., ADDED, MODIFIED, DELETED)
 * @param endpointSlice the resource object affected by the event
 */
public record Event(@JsonProperty("type") EventType type, @JsonProperty("object") EndpointSlice endpointSlice) {
}
