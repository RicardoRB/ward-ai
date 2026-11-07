package dev.ward.application.doctor.node;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.doctor.EnvironmentFacts;

/**
 * Hints at the input native TypeScript type resolution reads: the {@code typescript} declarations
 * in the project's {@code node_modules}. A missing input is a hint, never a failure.
 */
public final class TypeScriptTypeInputsFinding {
  private static final String NAME = "typescript-type-inputs";

  /** Set by the environment inspection when {@code typescript} resolves from the project root. */
  static final String TYPESCRIPT_RESOLVABLE = "typescriptResolvable";

  private TypeScriptTypeInputsFinding() {}

  /** Returns the hint for the inputs of native TypeScript type resolution. */
  public static Finding finding(final EnvironmentFacts facts) {
    if (facts.isSet(TYPESCRIPT_RESOLVABLE)) {
      return new Finding(
          NAME,
          Status.OK,
          "typescript resolves from node_modules, so native TypeScript type resolution has its"
              + " declarations.");
    }
    return new Finding(
        NAME,
        Status.WARNING,
        "hint: native TypeScript type resolution reads node_modules; declare typescript in the"
            + " project and install its dependencies.");
  }
}
