package com.healthassistant.dailysummary;

import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.dailysummary.api.dto.DailySummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
interface DailySummaryMapper {

    DailySummaryMapper INSTANCE = Mappers.getMapper(DailySummaryMapper.class);

    DailySummaryResponse toResponse(DailySummary summary);

    DailySummaryResponse.Activity toActivityResponse(DailySummary.Activity activity);

    DailySummaryResponse.Exercise toExerciseResponse(DailySummary.Exercise exercise);

    DailySummaryResponse.Workout toWorkoutResponse(DailySummary.Workout workout);

    DailySummaryResponse.Sleep toSleepResponse(DailySummary.Sleep sleep);

    DailySummaryResponse.Heart toHeartResponse(DailySummary.Heart heart);
}

