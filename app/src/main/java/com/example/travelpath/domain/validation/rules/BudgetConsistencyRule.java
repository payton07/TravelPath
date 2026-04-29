package com.example.travelpath.domain.validation.rules;

import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.domain.validation.ValidationResult;
import com.example.travelpath.domain.validation.ValidationRule;

public final class BudgetConsistencyRule implements ValidationRule {

    @Override
    public ValidationResult validate(SearchCriteria criteria) {
        if (criteria.getBudgetMin() > criteria.getBudgetMax()) {
            return ValidationResult.error("Minimum budget cannot exceed maximum budget.");
        }
        if (criteria.getBudgetMax() <= 0) {
            return ValidationResult.error("Budget must be greater than zero.");
        }
        return ValidationResult.ok();
    }
}
