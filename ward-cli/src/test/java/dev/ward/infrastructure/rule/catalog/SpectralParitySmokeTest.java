package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Runs the real Spectral on the fixtures of each implemented OpenAPI rule and compares what it
 * reports with the fixture's {@code expected} and {@code messages} files, which were written from
 * it. The report lists every rule case where the two differ, with the kind of difference. Skipped
 * unless {@code -Dward.spectral.dir} names a directory whose {@code node_modules} installs {@code
 * @stoplight/spectral-cli} 6.17.1 and {@code @stoplight/spectral-owasp-ruleset} 2.0.1; regenerate
 * the report with {@code -Dward.parity.write=true}.
 */
final class SpectralParitySmokeTest {

  private static final String DIRECTORY_PROPERTY = "ward.spectral.dir";
  private static final String WRITE_PROPERTY = "ward.parity.write";
  private static final String REPORT_FILE = "src/test/resources/parity/spectral-report.txt";
  private static final String RULESET = "ward-parity-ruleset.mjs";
  private static final Pattern RESULT =
      Pattern.compile(
          "\"code\":\\s*\"([^\"]+)\",\\s*\"path\":\\s*\\[[^\\]]*\\],\\s*\"message\":\\s*\"((?:\\\\.|[^\"\\\\])*)\",[\\s\\S]*?"
              + "\"range\":\\s*\\{\\s*\"start\":\\s*\\{\\s*\"line\":\\s*(\\d+),\\s*\"character\":\\s*(\\d+)");
  private static final int CODE = 1;
  private static final int MESSAGE = 2;
  private static final int LINE = 3;
  private static final int CHARACTER = 4;

  /** Whether the opt-in property names a Spectral install. */
  static boolean spectralConfigured() {
    return !System.getProperty(DIRECTORY_PROPERTY, "").isBlank();
  }

  @Test
  @EnabledIf("spectralConfigured")
  void theParityReportMatchesTheCommittedOne() throws IOException, InterruptedException {
    final Path install = Path.of(System.getProperty(DIRECTORY_PROPERTY));
    final Path ruleset = install.resolve(RULESET);
    Files.writeString(
        ruleset,
        "import { oas } from '@stoplight/spectral-rulesets';\n"
            + "import owasp from '@stoplight/spectral-owasp-ruleset';\n"
            + "export default { extends: [[oas, 'all'], owasp] };\n",
        StandardCharsets.UTF_8);
    final List<String> lines = new ArrayList<>();
    try {
      final Path root = RuleFixtureSet.root().resolve("openapi");
      for (final CatalogRow row : new RuleCatalogReader().load().rows()) {
        if (row.counterparts().containsKey("spectral")
            && row.languages(CatalogStatus.IMPLEMENTED).contains(AnalysisLanguage.OPENAPI)) {
          lines.addAll(differences(install, row, root.resolve(row.name())));
        }
      }
    } finally {
      Files.deleteIfExists(ruleset);
    }
    lines.sort(String::compareTo);
    final String report =
        "# rule, case, difference: tool-silent (Spectral reports nothing on the violating"
            + " fixture), positions-differ, messages-differ, tool-extra (Spectral reports the"
            + " passing fixture).\n"
            + "# Regenerate with -Dward.parity.write=true.\n"
            + String.join("\n", lines)
            + (lines.isEmpty() ? "" : "\n");
    if (Boolean.getBoolean(WRITE_PROPERTY)) {
      Files.writeString(Path.of(REPORT_FILE), report, StandardCharsets.UTF_8);
    }
    assertThat(report).isEqualTo(Files.readString(Path.of(REPORT_FILE), StandardCharsets.UTF_8));
  }

  private static List<String> differences(
      final Path install, final CatalogRow row, final Path directory)
      throws IOException, InterruptedException {
    final String id = row.counterparts().get("spectral").getFirst();
    final List<String> lines = new ArrayList<>();
    try (Stream<Path> files = Files.list(directory)) {
      for (final Path file : files.sorted().toList()) {
        final String name = file.getFileName().toString();
        if (name.startsWith("violating") || name.startsWith("passing")) {
          final String kind = kind(install, id, directory, file);
          if (!kind.isEmpty()) {
            lines.add(row.name() + "\t" + name + "\t" + kind);
          }
        }
      }
    }
    return lines;
  }

  private static String kind(
      final Path install, final String id, final Path directory, final Path file)
      throws IOException, InterruptedException {
    final List<String> reported = reported(install, id, file);
    final String name = file.getFileName().toString();
    if (name.startsWith("passing")) {
      return reported.isEmpty() ? "" : "tool-extra";
    }
    if (reported.isEmpty()) {
      return "tool-silent";
    }
    final String suffix = name.substring("violating".length(), name.length() - ".yaml".length());
    final List<String> messages =
        Files.readAllLines(directory.resolve("messages" + suffix + ".txt")).stream()
            .sorted()
            .toList();
    final List<String> positions =
        Files.readAllLines(directory.resolve("expected" + suffix + ".txt")).stream()
            .sorted()
            .toList();
    if (!reported.stream()
        .map(line -> line.substring(0, line.indexOf('\t')))
        .sorted()
        .toList()
        .equals(positions)) {
      return "positions-differ";
    }
    return reported.stream().sorted().toList().equals(messages) ? "" : "messages-differ";
  }

  /** One {@code line:column<TAB>message} for each finding of the rule {@code id} on the file. */
  private static List<String> reported(final Path install, final String id, final Path file)
      throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(
                install.resolve("node_modules/.bin/spectral").toString(),
                "lint",
                "-r",
                install.resolve(RULESET).toString(),
                "-f",
                "json",
                "--ignore-unknown-format",
                file.toString())
            .directory(install.toFile())
            .redirectErrorStream(false)
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    process.waitFor();
    final List<String> found = new ArrayList<>();
    final Matcher matcher = RESULT.matcher(output);
    while (matcher.find()) {
      if (matcher.group(CODE).equals(id)) {
        found.add(
            (Integer.parseInt(matcher.group(LINE)) + 1)
                + ":"
                + (Integer.parseInt(matcher.group(CHARACTER)) + 1)
                + "\t"
                + matcher.group(MESSAGE).replace("\\\"", "\"").replace("\\\\", "\\"));
      }
    }
    return found;
  }
}
