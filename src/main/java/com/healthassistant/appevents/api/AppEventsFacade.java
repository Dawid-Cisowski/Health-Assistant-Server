package com.healthassistant.appevents.api;

import com.healthassistant.appevents.api.dto.SubmitHealthEventsRequest;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;

public interface AppEventsFacade {
    StoreHealthEventsResult submitHealthEvents(SubmitHealthEventsRequest request, String authenticatedDeviceId);
}
