package dev.ward.analysis.domain.openapi.schema;

import java.util.function.BooleanSupplier;

/** Runs keyword checks in order and stops at the first failure unless every error is wanted. */
final class Chain {

  private Chain() {}

  /** Runs the steps; returns whether all of them held. */
  static boolean run(final JsonSchemaValidator validator, final BooleanSupplier... steps) {
    boolean valid = true;
    for (final BooleanSupplier step : steps) {
      valid &= step.getAsBoolean();
      if (!valid && !validator.allErrors()) {
        return false;
      }
    }
    return valid;
  }
}
