package io.github.lothar1998.kuberesolver.kubernetes.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * Represents an endpoint in a Kubernetes EndpointSlice, which includes the IP addresses
 * and readiness conditions of a network endpoint.
 *
 * @param addresses  a list of IP addresses for this endpoint
 * @param conditions readiness and health conditions associated with the endpoint
 */
public record Endpoint(@JsonProperty("addresses") @JsonSetter(nulls = Nulls.AS_EMPTY) List<String> addresses,
                       @JsonProperty("conditions") Conditions conditions) {

}
