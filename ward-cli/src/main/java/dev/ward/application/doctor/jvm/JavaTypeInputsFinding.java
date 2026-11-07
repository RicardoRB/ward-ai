package dev.ward.application.doctor.jvm;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.doctor.EnvironmentFacts;

/**
 * Hints at the inputs native Java type resolution reads: a JDK image, which a JDK provides, and the
 * {@code java-classpath} file that names library jars. A missing input is a hint, never a failure.
 */
public final class JavaTypeInputsFinding {
  private static final String NAME = "java-type-inputs";
  private static final String JDK_EXECUTABLE = "javac";

  private JavaTypeInputsFinding() {}

  /** Returns the hint for the inputs of native Java type resolution. */
  public static Finding finding(final EnvironmentFacts facts) {
    if (facts.hasExecutable(JDK_EXECUTABLE)) {
      return new Finding(
          NAME,
          Status.OK,
          "A JDK is available for native Java type resolution. Set java-classpath to a file of"
              + " library jars to resolve library types.");
    }
    return new Finding(
        NAME,
        Status.WARNING,
        "hint: native Java type resolution reads a JDK image; install a JDK and put javac on PATH.");
  }
}
