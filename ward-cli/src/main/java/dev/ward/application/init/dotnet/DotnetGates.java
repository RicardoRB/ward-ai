package dev.ward.application.init.dotnet;

import dev.ward.application.init.SourceLanguageGates;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C#'s gates on the .NET SDK: the build, the tests with Coverlet coverage and, when strict, Stryker.NET
 * mutation testing. The lint rules come from the {@code ward} gate and {@code ward-rules.toml}.
 * Strict makes warnings errors.
 */
public final class DotnetGates implements SourceLanguageGates<DotnetTools> {
  private static final List<String> STANDARD =
      List.of(DotnetTools.DOTNET_BUILD, DotnetTools.DOTNET_TEST, SupportedQualityGates.COVERAGE);
  private static final List<String> STRICT =
      List.of(
          DotnetTools.DOTNET_BUILD,
          DotnetTools.DOTNET_TEST,
          SupportedQualityGates.COVERAGE,
          DotnetTools.DOTNET_STRYKER
      );

  @Override
  public List<InitGateOption> analysisGates(final DotnetTools dotnet) {
    return List.of(
        option(
            dotnet,
            DotnetTools.DOTNET_BUILD,
            "Builds the solution.",
            dotnet.buildCommand(),
            Map.of()
        ),
        option(
            dotnet,
            DotnetTools.DOTNET_TEST,
            "Runs the tests, writing a Cobertura coverage report.",
            dotnet.testCommand(),
            Map.of()
        ),
        coverage(dotnet),
        option(
            dotnet,
            DotnetTools.DOTNET_STRYKER,
            "Mutation-tests the project with Stryker.NET; strict fails below a 60% mutation score.",
            dotnet.strykerCommand(),
            dotnet.strict()
                ? Map.of("minimum", String.valueOf(DotnetTools.STRICT_MUTATION))
                : Map.of()
        )
    );
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    return preset == QualityPreset.STRICT ? STRICT : STANDARD;
  }

  /** The {@code coverage} check: it runs no command and reads what the test gate wrote. */
  private static InitGateOption coverage(final DotnetTools dotnet) {
    final Map<String, String> settings = new LinkedHashMap<>();
    settings.put("minimum", String.valueOf(dotnet.coverageMinimum()));
    if (dotnet.strict()) {
      settings.put("minimum-branches", String.valueOf(DotnetTools.STRICT_BRANCHES));
    }
    settings.put("report", DotnetTools.COVERAGE_REPORT);
    return new InitGateOption(
        SupportedQualityGates.COVERAGE,
        "Fails below the coverage minimums in ward.toml.",
        List.of(),
        true,
        "Reads the Cobertura report that the dotnet-test gate writes; it runs no command.",
        settings
    );
  }

  private static InitGateOption option(
      final DotnetTools dotnet,
      final String id,
      final String description,
      final List<String> command,
      final Map<String,
      String> settings
  ) {
    return new InitGateOption(
        id,
        description,
        command,
        dotnet.available(),
        dotnet.available()
            ? "dotnet command available."
            : "dotnet is unavailable; install the .NET SDK from"
                + " https://dotnet.microsoft.com/download before running this gate.",
        settings
    );
  }
}
