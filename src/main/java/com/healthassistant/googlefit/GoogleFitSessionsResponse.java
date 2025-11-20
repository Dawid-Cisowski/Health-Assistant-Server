package com.healthassistant.googlefit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class GoogleFitSessionsResponse {
    @JsonProperty("session")
    private List<GoogleFitSession> sessions;

}

