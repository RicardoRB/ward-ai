package dev.ward.analysis.domain.port;

import java.util.Set;

/** Supplies the names a language provides without a declaration, such as Python's builtins. */
@FunctionalInterface
public interface BuiltinNames {

  /**
   * Returns the built-in names of the environments.
   *
   * @param environments the environments to include, such as {@code browser} or {@code node}; empty
   *     means the language's defaults
   * @return the names, never null
   * @throws AnalysisFailedException if an environment is not known to the language
   */
  Set<String> of(Set<String> environments);
}
