package dev.ward.infrastructure.format;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.frontend.antlr.format.csharp.CSharpFormatter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the real {@code dotnet format whitespace} at default settings on the input of every C#
 * formatter fixture and compares its output with Ward's. The outputs must be equal, except for the
 * fixtures that {@code parity/dotnet-format-approximations.txt} lists with their reason, whose
 * outputs must differ. Skipped unless {@code -Dward.parity.dotnet-format} names the {@code dotnet}
 * executable: {@code ./mvnw -pl ward-cli -am -Dtest=DotnetFormatParitySmokeTest
 * -Dward.parity.dotnet-format=<dotnet> -Dsurefire.failIfNoSpecifiedTests=false test}.
 */
@EnabledIfSystemProperty(named = "ward.parity.dotnet-format", matches = ".+")
final class DotnetFormatParitySmokeTest {
  private static final Path FIXTURES = Path.of(
      "../ward-frontend-antlr/src/test/resources/format/csharp"
  );
  private static final Path APPROXIMATIONS =
      Path.of("src/test/resources/parity/dotnet-format-approximations.txt");
  private static final long TIMEOUT_MINUTES = 2;
  private static final CSharpFormatter FORMATTER = new CSharpFormatter();

  @TempDir Path work;

  @Test
  void wardEqualsDotnetFormatExceptWhereListed() throws Exception {
    final Map<String, String> listed = approximations();
    final List<String> problems = new ArrayList<>();
    for (final Path fixture : fixtures()) {
      final String name = fixture.getFileName().toString();
      final String ward = ward(Files.readString(fixture.resolve("input.cs")));
      final boolean equal = ward.equals(dotnet(name, fixture.resolve("input.cs")));
      if (equal == listed.containsKey(name)) {
        problems.add(name + (equal ? ": equal but listed as different" : ": differs, not listed"));
      }
    }
    assertThat(problems).isEmpty();
    assertThat(listed.keySet()).allMatch(name -> Files.isDirectory(FIXTURES.resolve(name)));
  }

  private static Map<String, String> approximations() throws IOException {
    return Files.readAllLines(APPROXIMATIONS, StandardCharsets.UTF_8).stream()
        .filter(line -> !line.isBlank() && !line.startsWith("#"))
        .map(line -> line.split("\t", 2))
        .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1]));
  }

  /** The fixtures that must format, in name order. */
  private static List<Path> fixtures() throws IOException {
    try (Stream<Path> children = Files.list(FIXTURES)) {
      return children.filter(dir -> Files.exists(dir.resolve("expected.cs"))).sorted().toList();
    }
  }

  private static String ward(final String input) {
    final FormatResult result = FORMATTER.format(
        input,
        FormatOptions.defaults(FORMATTER.options())
    );
    return result instanceof FormatResult.Formatted formatted ? formatted.text() : input;
  }

  private String dotnet(
      final String name,
      final Path input
  ) throws IOException, InterruptedException {
    final Path folder = Files.createDirectories(work.resolve(name));
    final Path file = folder.resolve("input.cs");
    Files.copy(input, file);
    final Process process =
        new ProcessBuilder(
            System.getProperty("ward.parity.dotnet-format"),
            "format",
            "whitespace",
            "--folder",
            folder.toString()
        )
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
    try {
      assertThat(process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)).isTrue();
      assertThat(process.exitValue()).as("dotnet format of " + name).isZero();
      return Files.readString(file);
    } finally {
      process.destroyForcibly();
    }
  }
}
