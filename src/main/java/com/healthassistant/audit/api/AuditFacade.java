package com.healthassistant.audit.api;

import com.healthassistant.audit.api.dto.AuditTimelineResponse;

import java.time.LocalDate;

public interface AuditFacade {
    AuditTimelineResponse buildTimeline(String deviceId, LocalDate startDate, LocalDate endDate);
}
