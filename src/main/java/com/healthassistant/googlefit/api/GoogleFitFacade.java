package com.healthassistant.googlefit.api;

import java.time.LocalDate;
import java.util.List;

public interface GoogleFitFacade {

    HistoricalSyncResult syncHistory(int days);

    HistoricalSyncResult syncDates(List<LocalDate> dates);
}
