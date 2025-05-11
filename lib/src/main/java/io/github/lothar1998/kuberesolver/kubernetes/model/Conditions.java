package io.github.lothar1998.kuberesolver.kubernetes.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents readiness conditions for a Kubernetes endpoint.
 *
 * @param isReady indicates whether the endpoint is ready to receive traffic
 */
public record Conditions(@JsonProperty("ready") Boolean isReady) {
}
