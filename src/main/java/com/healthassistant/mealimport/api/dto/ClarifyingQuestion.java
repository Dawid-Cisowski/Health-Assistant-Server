package com.healthassistant.mealimport.api.dto;

import java.util.List;

public record ClarifyingQuestion(
    String questionId,
    String questionText,
    QuestionType questionType,
    List<String> options,
    List<String> affectedFields
) {
    public enum QuestionType {
        SINGLE_CHOICE,
        FREE_TEXT,
        YES_NO
    }
}
