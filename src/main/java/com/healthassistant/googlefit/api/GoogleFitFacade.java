package com.healthassistant.googlefit.api;

public interface GoogleFitFacade {

    void syncAll();

    HistoricalSyncResult syncHistory(int days);

    void deleteAllSyncState();
}
