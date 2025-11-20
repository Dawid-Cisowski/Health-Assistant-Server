package com.healthassistant.googlefit.api;

public record HistoricalSyncResult(int processedDays, int failedDays, int totalEvents) {
}
