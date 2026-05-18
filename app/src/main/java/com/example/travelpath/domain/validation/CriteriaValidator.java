package com.example.travelpath.domain.validation;

import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.domain.validation.rules.BudgetConsistencyRule;
import com.example.travelpath.domain.validation.rules.DestinationNotEmptyRule;
import com.example.travelpath.domain.validation.rules.DurationConsistencyRule;
import com.example.travelpath.domain.validation.rules.InterestsNotEmptyRule;

import java.util.Arrays;
import java.util.List;

/**
 * Validates a SearchCriteria through an ordered chain of rules.
 *
 * Design pattern: Chain of Responsibility.
 * Rules are evaluated in order; the first failure short-circuits the chain.
 * This keeps each rule small (Single Responsibility) while the validator
 * composes them without if-else ladders (Open/Closed for new rules).
 *
 * Usage:
 * <pre>
 *   ValidationResult result = CriteriaValidator.create().validate(criteria);
 *   if (!result.isValid()) showError(result.getErrorMessage());
 * </pre>
 */
public final class CriteriaValidator {

    private final List<ValidationRule> rules;

    private CriteriaValidator(List<ValidationRule> rules) {
        this.rules = rules;
    }

    /** Factory with the standard rule set for the explore screen. */
    public static CriteriaValidator create() {
        return new CriteriaValidator(Arrays.asList(
            new DestinationNotEmptyRule(),
            new InterestsNotEmptyRule(),
            new BudgetConsistencyRule(),
            new DurationConsistencyRule()
        ));
    }

    /** Factory for custom rule sets (e.g. in tests). */
    public static CriteriaValidator withRules(ValidationRule... rules) {
        return new CriteriaValidator(Arrays.asList(rules));
    }

    /** Runs all rules in order; returns on the first failure. */
    public ValidationResult validate(SearchCriteria criteria) {
        for (ValidationRule rule : rules) {
            ValidationResult result = rule.validate(criteria);
            if (!result.isValid()) return result;
        }
        return ValidationResult.ok();
    }
}
