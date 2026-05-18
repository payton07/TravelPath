package com.example.travelpath.domain.validation.rules;

import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.domain.validation.ValidationResult;
import com.example.travelpath.domain.validation.ValidationRule;

public final class InterestsNotEmptyRule implements ValidationRule {

    @Override
    public ValidationResult validate(SearchCriteria criteria) {
        if (criteria.getInterests() == null || criteria.getInterests().isEmpty()) {
            return ValidationResult.error("Please select at least one interest.");
        }
        return ValidationResult.ok();
    }
}
