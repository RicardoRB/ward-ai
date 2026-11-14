package dev.ward.domain.gate;

import java.util.List;
import java.util.Optional;

/** Reads the module directories a build file lists, from its text. */
@FunctionalInterface
public interface ModuleDirectories {
  /**
   * Returns the module directories the build lists, relative to the directory of the build file.
   *
   * @param pomText the text of {@code pom.xml}, if the directory has one
   * @param gradleSettingsText the text of {@code settings.gradle(.kts)}, if the directory has one
   */
  List<String> of(Optional<String> pomText, Optional<String> gradleSettingsText);
}
