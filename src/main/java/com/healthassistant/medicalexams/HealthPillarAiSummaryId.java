package com.healthassistant.medicalexams;

import java.io.Serializable;
import java.util.Objects;

class HealthPillarAiSummaryId implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceId;
    private String pillarCode;

    HealthPillarAiSummaryId() {}

    HealthPillarAiSummaryId(String deviceId, String pillarCode) {
        this.deviceId = deviceId;
        this.pillarCode = pillarCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HealthPillarAiSummaryId other)) return false;
        return Objects.equals(deviceId, other.deviceId) && Objects.equals(pillarCode, other.pillarCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, pillarCode);
    }
}
