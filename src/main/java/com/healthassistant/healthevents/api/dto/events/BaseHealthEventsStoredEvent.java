package com.healthassistant.healthevents.api.dto.events;

import com.healthassistant.healthevents.api.dto.StoredEventData;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public sealed interface BaseHealthEventsStoredEvent
        permits StepsEventsStoredEvent, WorkoutEventsStoredEvent, SleepEventsStoredEvent,
                ActivityEventsStoredEvent, CaloriesEventsStoredEvent, MealsEventsStoredEvent,
                WeightEventsStoredEvent, HeartRateEventsStoredEvent, RestingHeartRateEventsStoredEvent {

    List<StoredEventData> events();

    Set<LocalDate> affectedDates();
}
