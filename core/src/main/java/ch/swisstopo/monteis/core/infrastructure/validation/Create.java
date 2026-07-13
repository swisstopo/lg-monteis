package ch.swisstopo.monteis.core.infrastructure.validation;

import jakarta.validation.groups.Default;

/**
 * Validation group for create operations.
 *
 * <p>This marker interface is used with Jakarta Bean Validation to distinguish
 * constraints that should only be evaluated when a new entity is created.
 * By extending {@link Default}, constraints in the default validation group are
 * included automatically during create validation.
 */
public interface Create extends Default {}
