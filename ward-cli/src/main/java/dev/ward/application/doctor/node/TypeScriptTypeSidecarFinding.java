package dev.ward.application.doctor.node;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.doctor.EnvironmentFacts;

/**
 * Reports whether the TypeScript type sidecar can run: {@code node} on the PATH and the project's
 * {@code typescript} package resolvable from its root. Missing either is an optional warning.
 */
public final class TypeScriptTypeSidecarFinding {
  private static final String NAME = "typescript-type-sidecar";
  private static final String NODE = "node";

  /** Set by the environment inspection when {@code typescript} resolves from the project root. */
  static final String TYPESCRIPT_RESOLVABLE = "typescriptResolvable";

  private TypeScriptTypeSidecarFinding() {}

  /** Returns the optional finding for the TypeScript type sidecar of the project. */
  public static Finding finding(final EnvironmentFacts facts) {
    if (!facts.hasExecutable(NODE)) {
      return warning("optional: type-aware rules are skipped until node is on PATH.");
    }
    if (!facts.isSet(TYPESCRIPT_RESOLVABLE)) {
      return warning(
          "optional: type-aware TypeScript rules are skipped until typescript is installed in"
              + " this project.");
    }
    return new Finding(NAME, Status.OK, "The TypeScript type sidecar can run.");
  }

  private static Finding warning(final String message) {
    return new Finding(NAME, Status.WARNING, message);
  }
}
