package com.example.travelpath.domain.validation.rules;

import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.domain.validation.ValidationResult;
import com.example.travelpath.domain.validation.ValidationRule;

public final class DestinationNotEmptyRule implements ValidationRule {

    @Override
    public ValidationResult validate(SearchCriteria criteria) {
        String city = criteria.getDestinationCity();
        if (city == null || city.trim().isEmpty()) {
            return ValidationResult.error("Please enter a destination city.");
        }
        return ValidationResult.ok();
    }
}
