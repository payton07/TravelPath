package com.example.travelpath.domain.validation;

import com.example.travelpath.data.models.SearchCriteria;

/**
 * A single validation rule applied to SearchCriteria.
 *
 * Design pattern: Chain of Responsibility link.
 * Each implementation checks exactly one invariant and returns immediately,
 * allowing CriteriaValidator to short-circuit on the first failure.
 */
public interface ValidationRule {
    ValidationResult validate(SearchCriteria criteria);
}
