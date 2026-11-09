package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Runs the real Biome binary named by {@code -Dward.biome.binary} on the fixtures of the Biome rows
 * and compares its positions with each fixture's {@code expected.txt}. Skipped unless the property
 * is set. A fixture whose difference from Biome is known is listed in {@code
 * parity/biome-approximations.txt} with its reason.
 */
class BiomeParitySmokeTest {

  private static final String BINARY = System.getProperty("ward.biome.binary", "");
  private static final Pattern DIAGNOSTIC = Pattern.compile(":(\\d+):(\\d+) lint/");
  private static final Path APPROXIMATIONS =
      Path.of("src/test/resources/parity/biome-approximations.txt");
  private static final List<String> LANGUAGES = List.of("javascript", "typescript");

  /** The Biome rule of each catalog row, as {@code group/rule}. */
  private static final Map<String, String> BIOME =
      Map.ofEntries(
          Map.entry(
              "no-approximative-numeric-constant", "suspicious/noApproximativeNumericConstant"),
          Map.entry("use-flat-math-min-max", "nursery/useFlatMathMinMax"),
          Map.entry("use-math-min-max", "nursery/useMathMinMax"),
          Map.entry("use-modern-math-apis", "nursery/useModernMathApis"),
          Map.entry("use-dom-node-text-content", "nursery/useDomNodeTextContent"),
          Map.entry("use-default-switch-clause-last", "suspicious/useDefaultSwitchClauseLast"),
          Map.entry("no-static-only-class", "complexity/noStaticOnlyClass"),
          Map.entry("no-useless-lone-block-statements", "complexity/noUselessLoneBlockStatements"),
          Map.entry("use-simple-number-keys", "complexity/useSimpleNumberKeys"),
          Map.entry("no-empty-type-parameters", "complexity/noEmptyTypeParameters"),
          Map.entry("no-return-in-finally", "nursery/noReturnInFinally"),
          Map.entry("no-constant-math-min-max-clamp", "correctness/noConstantMathMinMaxClamp"),
          Map.entry(
              "no-dynamic-namespace-import-access", "performance/noDynamicNamespaceImportAccess"),
          Map.entry("no-unused-function-parameters", "correctness/noUnusedFunctionParameters"),
          Map.entry("no-base-to-string", "nursery/noBaseToString"),
          Map.entry("no-unreachable-super", "correctness/noUnreachableSuper"),
          Map.entry("no-access-key", "a11y/noAccessKey"),
          Map.entry("no-autofocus", "a11y/noAutofocus"),
          Map.entry("no-positive-tabindex", "a11y/noPositiveTabindex"),
          Map.entry("no-distracting-elements", "a11y/noDistractingElements"),
          Map.entry("no-blank-target", "security/noBlankTarget"),
          Map.entry("no-duplicate-jsx-props", "suspicious/noDuplicateJsxProps"),
          Map.entry("no-dangerously-set-inner-html", "security/noDangerouslySetInnerHtml"),
          Map.entry("no-jsx-props-bind", "performance/noJsxPropsBind"),
          Map.entry("no-obsolete-tags", "nursery/noObsoleteTags"),
          Map.entry("no-jsx-namespace", "nursery/noJsxNamespace"),
          Map.entry("use-button-type", "a11y/useButtonType"),
          Map.entry("use-alt-text", "a11y/useAltText"),
          Map.entry("use-iframe-title", "a11y/useIframeTitle"),
          Map.entry("use-html-lang", "a11y/useHtmlLang"),
          Map.entry("use-key-with-click-events", "a11y/useKeyWithClickEvents"),
          Map.entry("use-key-with-mouse-events", "a11y/useKeyWithMouseEvents"));

  @Test
  void biomeReportsTheSamePositionsAsTheFixtures() throws IOException, InterruptedException {
    assumeTrue(!BINARY.isBlank(), "set -Dward.biome.binary to run the Biome parity smoke test");
    final Set<String> approximated = approximated();
    final List<String> problems = new ArrayList<>();
    for (final Map.Entry<String, String> row : BIOME.entrySet()) {
      for (final String language : LANGUAGES) {
        final Path dir = RuleFixtureSet.root().resolve(language).resolve(row.getKey());
        if (!Files.isDirectory(dir) || approximated.contains(row.getKey() + "/" + language)) {
          continue;
        }
        problems.addAll(compare(dir, row.getValue(), row.getKey() + "/" + language));
      }
    }
    assertThat(problems).isEmpty();
  }

  private static List<String> compare(final Path dir, final String group, final String name)
      throws IOException, InterruptedException {
    final List<String> problems = new ArrayList<>();
    try (Stream<Path> files = Files.list(dir)) {
      for (final Path file : files.sorted().toList()) {
        final String fileName = file.getFileName().toString();
        if (!fileName.startsWith("violating.") && !fileName.startsWith("passing.")) {
          continue;
        }
        final Set<String> expected = fileName.startsWith("violating.") ? expected(dir) : Set.of();
        final Set<String> biome = biome(file, group);
        if (!biome.equals(expected)) {
          problems.add(name + " " + fileName + " biome " + biome + " fixture " + expected);
        }
      }
    }
    return problems;
  }

  private static Set<String> expected(final Path dir) throws IOException {
    return new TreeSet<>(
        Files.readAllLines(dir.resolve("expected.txt")).stream()
            .map(String::strip)
            .filter(line -> !line.isEmpty())
            .toList());
  }

  private static Set<String> biome(final Path file, final String group)
      throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(
                BINARY, "lint", "--only=" + group, "--max-diagnostics=200", file.toString())
            .redirectErrorStream(true)
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    process.waitFor();
    final Set<String> positions = new TreeSet<>();
    final Matcher matcher = DIAGNOSTIC.matcher(output);
    while (matcher.find()) {
      positions.add(matcher.group(1) + ":" + matcher.group(2));
    }
    return positions;
  }

  private static Set<String> approximated() throws IOException {
    final Set<String> names = new TreeSet<>();
    if (!Files.isRegularFile(APPROXIMATIONS)) {
      return names;
    }
    for (final String line : Files.readAllLines(APPROXIMATIONS)) {
      final String entry = line.strip();
      if (!entry.isEmpty() && !entry.startsWith("#")) {
        names.add(entry.split(" ", 2)[0]);
      }
    }
    return names;
  }
}
