package com.healthassistant.googlefit;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record GoogleFitSessionsResponse(
        @JsonProperty("session")
        List<GoogleFitSession> sessions
) {
}

