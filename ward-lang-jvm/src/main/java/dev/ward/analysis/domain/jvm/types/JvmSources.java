package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SourceUnit;
import java.util.Optional;

/** The source files of the project, found by the qualified name of the top-level type they hold. */
@FunctionalInterface
public interface JvmSources {

  /**
   * Returns the source file that declares the top-level type {@code qualifiedName}.
   *
   * @param qualifiedName the package and name, such as {@code app.User}
   * @return the unit, empty when no source root holds a file for it
   */
  Optional<SourceUnit> declaring(String qualifiedName);
}
