package com.healthassistant.googlefit.api;

import java.time.LocalDate;

public interface GoogleFitFacade {

    SyncDayResult syncDay(LocalDate date);
}
