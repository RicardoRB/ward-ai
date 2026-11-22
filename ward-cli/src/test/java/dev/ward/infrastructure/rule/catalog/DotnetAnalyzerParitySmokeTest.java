package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Runs the real .NET analyzers (Microsoft.CodeAnalysis.NetAnalyzers 9.0.0 and CodeStyle 4.14.0) and
 * StyleCop.Analyzers 1.2.0.556 through {@code dotnet build} on the fixtures of every implemented
 * C# row, with only the row's counterpart rule on, and compares the lines it reports with {@code
 * expected.txt}. The result is {@code parity/dotnet-report.txt}, one line per row, tool id and
 * outcome: {@code agree}, or {@code approximation} with the reason of {@code
 * parity/dotnet-approximations.txt}. A difference without a reason fails the test. Skipped unless
 * {@code -Dward.parity.dotnet} names the {@code dotnet} executable (needs NuGet); {@code
 * -Dward.parity.write=true} rewrites the report.
 */
final class DotnetAnalyzerParitySmokeTest {
  private static final String PROPERTY = "ward.parity.dotnet";
  private static final String AGREE = "agree";
  private static final int REASON_PARTS = 3;
  private static final String WRITE = "ward.parity.write";
  private static final Path REPORT = Path.of("src/test/resources/parity/dotnet-report.txt");
  private static final Path REASONS =
      Path.of("src/test/resources/parity/dotnet-approximations.txt");
  private static final Pattern DIAGNOSTIC =
      Pattern.compile("\\.cs\\((\\d+),\\d+\\): (?:warning|error) ([A-Z]+\\d{4}):");
  private static final String GLOBALS = "global using System;\nglobal using System.IO;\n";
  private static final String STYLECOP_JSON =
      "{\"settings\":{\"orderingRules\":{\"usingDirectivesPlacement\":\"outsideNamespace\"}}}";
  private static final Map<String, String> OPTIONS =
      Map.of(
          "IDE0011",
          "csharp_prefer_braces = true:warning\n",
          "IDE0161",
          "csharp_style_namespace_declarations = file_scoped:warning\n"
      );
  private static final String PROJECT =
      """
      <Project Sdk="Microsoft.NET.Sdk">
        <PropertyGroup>
          <TargetFramework>net9.0</TargetFramework>
          <OutputType>Library</OutputType>
          <Nullable>disable</Nullable>
          <ImplicitUsings>disable</ImplicitUsings>
          <EnforceCodeStyleInBuild>true</EnforceCodeStyleInBuild>
          <AnalysisLevel>9.0-all</AnalysisLevel>
          <GenerateDocumentationFile>false</GenerateDocumentationFile>
        </PropertyGroup>
        <ItemGroup>
          <AdditionalFiles Include="stylecop.json" />
          <AdditionalFiles Include="CodeMetricsConfig.txt" />
          <PackageReference Include="Microsoft.CodeAnalysis.NetAnalyzers" Version="9.0.0" PrivateAssets="all" />
          <PackageReference Include="Microsoft.CodeAnalysis.CSharp.CodeStyle" Version="4.14.0" PrivateAssets="all" />
          <PackageReference Include="StyleCop.Analyzers" Version="1.2.0-beta.556" PrivateAssets="all" />
        </ItemGroup>
      </Project>
      """;

  /** Whether the opt-in property names a dotnet executable. */
  static boolean dotnetConfigured() {
    return !System.getProperty(PROPERTY, "").isBlank();
  }

  @Test
  @DisplayName("parity with the .NET analyzers and StyleCop.Analyzers")
  @EnabledIf("dotnetConfigured")
  void theDotnetAnalyzersReportTheExpectedLinesOfEachCSharpFixture()
      throws IOException, InterruptedException {
    final String dotnet = System.getProperty(PROPERTY);
    final Map<String, String> reasons = reasons();
    final Map<String, String> report = new TreeMap<>();
    final List<String> problems = new ArrayList<>();
    final Path root = RuleFixtureSet.root().resolve("csharp");
    for (final CatalogRow row : new RuleCatalogReader().load().rows()) {
      if (!row.languages(CatalogStatus.IMPLEMENTED).contains(AnalysisLanguage.CSHARP)
          || !Files.exists(root.resolve(row.name()).resolve("violating.cs"))) {
        continue;
      }
      for (final String tool : List.of("dotnet-analyzers", "dotnet-code-style", "stylecop")) {
        for (final String id : row.counterparts().getOrDefault(tool, List.of())) {
          final String key = row.name() + " " + id;
          final String outcome = outcome(dotnet, id, root.resolve(row.name()));
          if (AGREE.equals(outcome)) {
            report.put(key, AGREE);
          } else if (reasons.containsKey(key)) {
            report.put(key, "approximation " + reasons.get(key));
          } else {
            problems.add(key + ": " + outcome);
          }
        }
      }
    }
    assertThat(problems).isEmpty();
    final List<String> lines = report.entrySet().stream().map(
        e -> e.getKey() + " " + e.getValue()
    ).toList();
    if (Boolean.getBoolean(WRITE)) {
      Files.write(REPORT, lines, StandardCharsets.UTF_8);
    }
    assertThat(lines).isEqualTo(Files.readAllLines(REPORT, StandardCharsets.UTF_8));
  }

  private static String outcome(final String dotnet, final String id, final Path directory)
      throws IOException, InterruptedException {
    final List<Integer> expected = expectedLines(directory.resolve("expected.txt"));
    final List<Integer> violating = reported(dotnet, id, directory.resolve("violating.cs"));
    final List<Integer> passing = reported(dotnet, id, directory.resolve("passing.cs"));
    if (!passing.isEmpty()) {
      return "tool reports " + passing + " on the passing fixture";
    }
    return violating.equals(expected) ? AGREE : "tool " + violating + ", fixture " + expected;
  }

  private static List<Integer> reported(final String dotnet, final String id, final Path fixture)
      throws IOException, InterruptedException {
    final Path project = Files.createTempDirectory("ward-dotnet-parity-");
    Files.writeString(project.resolve("Parity.csproj"), PROJECT, StandardCharsets.UTF_8);
    Files.writeString(
        project.resolve(".editorconfig"),
        "root = true\n[*.cs]\ndotnet_analyzer_diagnostic.severity = none\ndotnet_diagnostic."
            + id
            + ".severity = warning\n"
            + OPTIONS.getOrDefault(id, ""),
        StandardCharsets.UTF_8
    );
    Files.writeString(project.resolve("Globals.cs"), GLOBALS, StandardCharsets.UTF_8);
    Files.writeString(project.resolve("stylecop.json"), STYLECOP_JSON, StandardCharsets.UTF_8);
    Files.writeString(
        project.resolve("CodeMetricsConfig.txt"),
        "CA1502: 10\n",
        StandardCharsets.UTF_8
    );
    Files.copy(fixture, project.resolve("Fixture.cs"));
    final Process process =
        new ProcessBuilder(dotnet, "build", "--nologo", "-v", "q", "-clp:NoSummary")
            .directory(project.toFile())
            .redirectErrorStream(true)
            .start();
    final String output = new String(
        process.getInputStream().readAllBytes(),
        StandardCharsets.UTF_8
    );
    process.waitFor();
    final List<Integer> lines = new ArrayList<>();
    final Matcher matcher = DIAGNOSTIC.matcher(output);
    while (matcher.find()) {
      if (matcher.group(2).equals(id)) {
        lines.add(Integer.parseInt(matcher.group(1)));
      }
    }
    return new ArrayList<>(new TreeSet<>(lines));
  }

  private static List<Integer> expectedLines(final Path file) throws IOException {
    return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
        .filter(line -> !line.isBlank())
        .map(line -> Integer.parseInt(line.substring(0, line.indexOf(':'))))
        .distinct()
        .toList();
  }

  private static Map<String, String> reasons() throws IOException {
    final Map<String, String> reasons = new TreeMap<>();
    if (Files.exists(REASONS)) {
      for (final String line : Files.readAllLines(REASONS, StandardCharsets.UTF_8)) {
        final String[] parts = line.split(" ", REASON_PARTS);
        if (parts.length == REASON_PARTS && !line.startsWith("#")) {
          reasons.put(parts[0] + " " + parts[1], parts[2]);
        }
      }
    }
    return reasons;
  }
}
