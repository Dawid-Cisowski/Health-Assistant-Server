package com.healthassistant.bodymeasurements.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum BodyPart {
    BICEPS_LEFT("biceps-left"),
    BICEPS_RIGHT("biceps-right"),
    FOREARM_LEFT("forearm-left"),
    FOREARM_RIGHT("forearm-right"),
    CHEST("chest"),
    WAIST("waist"),
    ABDOMEN("abdomen"),
    HIPS("hips"),
    NECK("neck"),
    SHOULDERS("shoulders"),
    THIGH_LEFT("thigh-left"),
    THIGH_RIGHT("thigh-right"),
    CALF_LEFT("calf-left"),
    CALF_RIGHT("calf-right");

    private final String value;

    BodyPart(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static BodyPart fromValue(String value) {
        return java.util.Arrays.stream(values())
                .filter(bp -> bp.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown body part: " + value));
    }
}
