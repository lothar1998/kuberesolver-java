package io.github.lothar1998.kuberesolver.kubernetes.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum EventType {
    @JsonProperty("ADDED")
    ADDED,
    @JsonProperty("MODIFIED")
    MODIFIED,
    @JsonProperty("DELETED")
    DELETED,
    @JsonProperty("ERROR")
    ERROR,
    @JsonProperty("BOOKMARK")
    BOOKMARK,
    @JsonEnumDefaultValue
    UNKNOWN
}
