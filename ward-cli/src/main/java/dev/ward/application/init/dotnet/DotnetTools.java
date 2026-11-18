package dev.ward.application.init.dotnet;

import dev.ward.domain.init.QualityPreset;
import java.util.ArrayList;
import java.util.List;

/**
 * Gate choices that run the .NET SDK on a solution or project.
 *
 * @param available whether {@code dotnet} is on the path
 * @param preset the selected preset, or {@code null} for a custom selection
 */
record DotnetTools(boolean available, QualityPreset preset) {
  static final String DOTNET_BUILD = "dotnet-build";
  static final String DOTNET_TEST = "dotnet-test";

  private static final String WARNINGS_AS_ERRORS = "-warnaserror";

  /** Returns whether the preset is strict. */
  boolean strict() {
    return preset == QualityPreset.STRICT;
  }

  /** Returns the build of the solution or project, warnings as errors when strict. */
  List<String> buildCommand() {
    return withStrictFlags(List.of("dotnet", "build"));
  }

  /** Returns the test run of the solution or project, warnings as errors when strict. */
  List<String> testCommand() {
    return withStrictFlags(List.of("dotnet", "test"));
  }

  private List<String> withStrictFlags(final List<String> command) {
    if (!strict()) {
      return command;
    }
    final List<String> strictCommand = new ArrayList<>(command);
    strictCommand.add(WARNINGS_AS_ERRORS);
    return List.copyOf(strictCommand);
  }
}
