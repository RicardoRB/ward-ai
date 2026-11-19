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

  static final String DOTNET_STRYKER = "stryker-net";
  static final String COVERAGE_DIRECTORY = ".ward/reports/dotnet";
  static final String COVERAGE_REPORT = COVERAGE_DIRECTORY + "/*/coverage.cobertura.xml";
  static final String STRYKER_DIRECTORY = ".ward/stryker";
  static final int STRICT_BRANCHES = 75;
  static final int STRICT_MUTATION = 60;

  private static final int STANDARD_COVERAGE = 80;
  private static final int STRICT_COVERAGE = 85;
  private static final String WARNINGS_AS_ERRORS = "-warnaserror";

  /** Returns whether the preset is strict. */
  boolean strict() {
    return preset == QualityPreset.STRICT;
  }

  /** Returns the build of the solution or project, warnings as errors when strict. */
  List<String> buildCommand() {
    return withStrictFlags(List.of("dotnet", "build"));
  }

  /**
   * Returns the test run of the solution or project, warnings as errors when strict. Coverlet's
   * collector, which the test project templates reference, writes Cobertura reports below {@link
   * #COVERAGE_DIRECTORY}, one directory per run.
   */
  List<String> testCommand() {
    return withStrictFlags(
        List.of(
            "dotnet",
            "test",
            "--collect:XPlat Code Coverage",
            "--results-directory",
            COVERAGE_DIRECTORY
        )
    );
  }

  /** Returns Stryker.NET's run, which writes its JSON report below {@link #STRYKER_DIRECTORY}. */
  List<String> strykerCommand() {
    return List.of("dotnet", "stryker", "--reporter", "json", "--output", STRYKER_DIRECTORY);
  }

  /** Returns the line minimum of the preset, in percent. */
  int coverageMinimum() {
    return strict() ? STRICT_COVERAGE : STANDARD_COVERAGE;
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
