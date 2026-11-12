package dev.ward.domain.gate;

import java.util.List;
import java.util.Optional;

/** Reads the main source roots of a module from the text of its build file. */
@FunctionalInterface
public interface MainSourceRoots {
  /**
   * Returns the main source roots relative to the module.
   *
   * @param pomText the text of {@code pom.xml}, if the module has one
   * @param gradleText the text of its Gradle build script, if it has one
   */
  List<String> of(Optional<String> pomText, Optional<String> gradleText);
}
