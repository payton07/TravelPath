package com.example.travelpath.domain.validation.rules;

import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.domain.validation.ValidationResult;
import com.example.travelpath.domain.validation.ValidationRule;

public final class DurationConsistencyRule implements ValidationRule {

    private static final double MIN_VIABLE_HOURS = 1.0;

    @Override
    public ValidationResult validate(SearchCriteria criteria) {
        if (criteria.getDurationMinHours() > criteria.getDurationMaxHours()) {
            return ValidationResult.error("Minimum duration cannot exceed maximum duration.");
        }
        if (criteria.getDurationMaxHours() < MIN_VIABLE_HOURS) {
            return ValidationResult.error("Trip duration must be at least 1 hour.");
        }
        return ValidationResult.ok();
    }
}
