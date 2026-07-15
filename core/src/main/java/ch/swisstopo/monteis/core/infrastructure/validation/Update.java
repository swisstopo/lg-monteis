package ch.swisstopo.monteis.core.infrastructure.validation;

import jakarta.validation.groups.Default;

/**
 * Validation group for update operations.
 *
 * <p>This marker interface is used with Jakarta Bean Validation to distinguish
 * constraints that should only be evaluated when an existing entity is updated.
 * By extending {@link Default}, constraints in the default validation group are
 * included automatically during update validation.
 */
public interface Update extends Default {}
