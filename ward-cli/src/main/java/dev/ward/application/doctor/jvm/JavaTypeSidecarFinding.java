package dev.ward.application.doctor.jvm;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.doctor.EnvironmentFacts;

/**
 * Reports whether the Java type sidecar can run: it compiles with {@code javac}, so a JDK with
 * {@code javac} on the PATH is enough. Missing it is optional: only type-aware rules are skipped.
 */
public final class JavaTypeSidecarFinding {
  private static final String NAME = "java-type-sidecar";
  private static final String COMPILER = "javac";

  private JavaTypeSidecarFinding() {}

  /** Returns the optional finding for the Java type sidecar. */
  public static Finding finding(final EnvironmentFacts facts) {
    if (facts.hasExecutable(COMPILER)) {
      return new Finding(NAME, Status.OK, "The Java type sidecar can run: javac is available.");
    }
    return new Finding(
        NAME,
        Status.WARNING,
        "optional: type-aware Java rules are not run until a JDK with javac is on PATH (they also need a java-classpath file).");
  }
}
