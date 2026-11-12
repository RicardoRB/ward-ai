package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleFileReader;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

/** The generated secret rules hold the rules of the pinned Gitleaks list and compile in Java. */
final class SecretRuleDataTest {

  private static final Path PINNED =
      Path.of("src/test/resources/catalog/upstream/gitleaks-8.30.1.txt");
  private static final String PREFIX = "ward:secrets/";

  @Test
  void idsEqualThePinnedGitleaksList() throws IOException {
    final List<String> pinned =
        Files.readAllLines(PINNED).stream()
            .filter(line -> !line.startsWith("#") && !line.isBlank())
            .map(line -> PREFIX + line.split("\t")[0])
            .sorted()
            .toList();

    final List<String> ids = rules().stream().map(RuleDefinition::id).sorted().toList();

    assertThat(ids).hasSize(222).containsExactlyElementsOf(pinned);
  }

  @Test
  void everyRegexAndPathPatternCompilesInJava() {
    for (final RuleDefinition rule : rules()) {
      final TomlParseResult body = Toml.parse(rule.body());
      assertThat(body.hasErrors()).as(rule.id()).isFalse();
      for (final String key : List.of("regex", "path")) {
        if (body.contains(key)) {
          assertThat(Pattern.compile(body.getString(key), Pattern.UNIX_LINES))
              .as(rule.id())
              .isNotNull();
        }
      }
    }
  }

  @Test
  void everyRuleCanBeCompiledByTheMatcher() {
    final DeclarativeRuleMatcher matcher = new DeclarativeRuleMatcher();
    rules().forEach(rule -> assertThat(matcher.compile(rule)).as(rule.id()).isNotNull());
  }

  @Test
  void posixAndNamedGroupTranslationsArePresent() {
    final String data = String.join("\n", bodies());

    assertThat(data).doesNotContain("[[:alnum:]]").doesNotContain("(?P<");
    assertThat(data).contains("p{Alnum}");
  }

  @Test
  void theFiveRulesWithAPathAndTheGlobalListsArePresent() throws IOException {
    final long withPath =
        rules().stream().filter(rule -> Toml.parse(rule.body()).contains("path")).count();
    final TomlParseResult global =
        Toml.parse(Files.readString(Path.of("src/main/resources/" + SecretAllowlistFile.RESOURCE)));

    assertThat(withPath).isEqualTo(5);
    assertThat(size(global.getArray("paths"))).isPositive();
    assertThat(size(global.getArray("regexes"))).isPositive();
    assertThat(size(global.getArray("stopwords"))).isPositive();
  }

  private static int size(final TomlArray array) {
    return array.size();
  }

  private static List<String> bodies() {
    return rules().stream().map(RuleDefinition::body).collect(Collectors.toList());
  }

  private static List<RuleDefinition> rules() {
    return RuleFileReader.read("rules/secrets/secrets.toml");
  }
}
