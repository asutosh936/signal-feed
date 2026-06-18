package com.signalfeed.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AITool(
        @JsonProperty("name") String name,
        @JsonProperty("category") String category,
        @JsonProperty("description") String description,
        @JsonProperty("pros") List<String> pros,
        @JsonProperty("cons") List<String> cons,
        @JsonProperty("link") String link
) {}
