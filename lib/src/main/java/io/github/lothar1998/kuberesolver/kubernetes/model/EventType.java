
package io.github.lothar1998.kuberesolver.kubernetes.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Represents the type of Kubernetes watch event. These types are used to indicate
 * what kind of change has occurred to a Kubernetes resource.
 */
public enum EventType {
    /**
     * Indicates that a new resource has been added.
     */
    @JsonProperty("ADDED")
    ADDED,

    /**
     * Indicates that an existing resource has been modified.
     */
    @JsonProperty("MODIFIED")
    MODIFIED,

    /**
     * Indicates that a resource has been deleted.
     */
    @JsonProperty("DELETED")
    DELETED,

    /**
     * Indicates that an error has occurred.
     */
    @JsonProperty("ERROR")
    ERROR,

    /**
     * Used for bookmarks, representing a resource version marker.
     */
    @JsonProperty("BOOKMARK")
    BOOKMARK,

    /**
     * Fallback type when the event type is unrecognized.
     */
    @JsonEnumDefaultValue
    UNKNOWN
}
