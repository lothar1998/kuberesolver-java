package io.github.lothar1998.kuberesolver.kubernetes.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Conditions(@JsonProperty("ready") Boolean isReady) {
}
