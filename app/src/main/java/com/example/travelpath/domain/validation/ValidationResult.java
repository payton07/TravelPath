package com.example.travelpath.domain.validation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Immutable result of a validation check.
 *
 * Design pattern: Value Object — carries meaning without identity.
 * Two ValidationResults are equivalent if they have the same valid flag and message.
 */
public final class ValidationResult {

    private final boolean valid;
    @Nullable private final String errorMessage;

    private ValidationResult(boolean valid, @Nullable String errorMessage) {
        this.valid        = valid;
        this.errorMessage = errorMessage;
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult error(@NonNull String message) {
        return new ValidationResult(false, message);
    }

    public boolean isValid() { return valid; }

    @Nullable
    public String getErrorMessage() { return errorMessage; }
}
