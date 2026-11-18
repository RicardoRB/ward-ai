package dev.ward.application.init.dotnet;

import dev.ward.application.init.SourceLanguageGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.List;
import java.util.Map;

/**
 * C#'s gates on the .NET SDK: the build and the tests of the solution or project. The lint rules
 * come from the {@code ward} gate and {@code ward-rules.toml}. Strict makes warnings errors.
 */
public final class DotnetGates implements SourceLanguageGates<DotnetTools> {
  private static final List<String> STANDARD =
      List.of(DotnetTools.DOTNET_BUILD, DotnetTools.DOTNET_TEST);

  @Override
  public List<InitGateOption> analysisGates(final DotnetTools dotnet) {
    return List.of(
        option(dotnet, DotnetTools.DOTNET_BUILD, "Builds the solution.", dotnet.buildCommand()),
        option(dotnet, DotnetTools.DOTNET_TEST, "Runs the tests.", dotnet.testCommand())
    );
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    return STANDARD;
  }

  private static InitGateOption option(
      final DotnetTools dotnet,
      final String id,
      final String description,
      final List<String> command
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
        Map.of()
    );
  }
}
