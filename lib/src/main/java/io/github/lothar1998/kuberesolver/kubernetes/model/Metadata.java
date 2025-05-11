package io.github.lothar1998.kuberesolver.kubernetes.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents metadata for a Kubernetes resource, typically including the resource name.
 *
 * @param name the name of the Kubernetes resource
 */
public record Metadata(@JsonProperty("name") String name) {
}
