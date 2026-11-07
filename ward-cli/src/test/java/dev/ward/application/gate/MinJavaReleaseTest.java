package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * A rule that suggests newer Java features names the lowest release it applies to. The values come
 * from {@code minimumLanguageVersion} in the rule XML of the pinned PMD jar, and from the release
 * that introduced the feature for the other rules.
 */
class MinJavaReleaseTest {

  private static final String USE_EXPLICIT_TYPES = "ward:java/use-explicit-types";
  private static final Map<String, Integer> SEEDED =
      Map.ofEntries(
          Map.entry("ward:java/avoid-file-stream", 7),
          Map.entry("ward:java/identical-catch-branches", 7),
          Map.entry("ward:java/replace-java-util-calendar", 8),
          Map.entry("ward:java/replace-java-util-date", 8),
          Map.entry("ward:java/use-diamond-operator", 7),
          Map.entry(USE_EXPLICIT_TYPES, 10),
          Map.entry("ward:java/use-standard-charsets", 7),
          Map.entry("ward:java/use-try-with-resources", 7),
          Map.entry("ward:java/use-underscores-in-numeric-literals", 7),
          Map.entry("ward:java/pattern-variable-assignment", 16),
          Map.entry("ward:java/pattern-variable-name", 16),
          Map.entry("ward:java/record-accessor-in-compact-constructor", 16),
          Map.entry("ward:java/missing-override-on-record-accessor", 16),
          Map.entry("ward:java/record-component-name", 16),
          Map.entry("ward:java/record-type-parameter-name", 16),
          Map.entry("ward:java/record-component-number", 16),
          Map.entry("ward:java/sealed-should-have-permits-list", 17),
          Map.entry("ward:java/text-block-google-style-formatting", 15),
          Map.entry("ward:java/switch-expression-nullable", 14));

  @Test
  void theBundledRulesDeclareExactlyTheSeededReleases() {
    final Map<String, Integer> declared =
        new ClasspathRuleSource()
            .load().rules().stream()
                .filter(rule -> rule.minJavaRelease() > 0)
                .collect(Collectors.toMap(RuleDefinition::id, RuleDefinition::minJavaRelease));

    assertThat(declared).containsExactlyInAnyOrderEntriesOf(SEEDED);
  }

  @Test
  void aKnownLowerReleaseSkipsTheRuleAndAnUnknownOneKeepsIt() {
    final RuleDefinition rule = rule(USE_EXPLICIT_TYPES);

    assertThat(rule.runsOn(8)).isFalse();
    assertThat(rule.runsOn(10)).isTrue();
    assertThat(rule.runsOn(JavaRelease.UNKNOWN)).isTrue();
  }

  @Test
  void theSetupSkipsARuleAboveTheProjectsRelease() {
    final ConfiguredRuleSetup setup =
        new ConfiguredRuleSetup(
            "ward",
            null,
            new ConfiguredRuleSetup.Choices(
                List.of(rule(USE_EXPLICIT_TYPES)),
                false,
                List.of(rule(USE_EXPLICIT_TYPES)),
                List.of()),
            new ClasspathRuleSource());

    assertThat(setup.resolve(java.nio.file.Path.of("."), 8).rules()).isEmpty();
    assertThat(setup.resolve(java.nio.file.Path.of("."), 11).rules()).hasSize(1);
    assertThat(setup.resolve(java.nio.file.Path.of("."), JavaRelease.UNKNOWN).rules()).hasSize(1);
  }

  @Test
  void theResolverSkipsARuleAboveTheProjectsRelease() {
    final RuleConfigFile file =
        new RuleConfigFile(
            "ward-rules.toml",
            new RuleConfigFile.Select(
                null, List.of(), List.of(), List.of(USE_EXPLICIT_TYPES), List.of()),
            Map.of(USE_EXPLICIT_TYPES, Map.of("severity", "warning")),
            List.of());
    final List<RuleDefinition> catalog = List.of(rule(USE_EXPLICIT_TYPES));

    assertThat(RuleConfigResolver.resolve(file, catalog, 8).rules()).isEmpty();
    assertThat(RuleConfigResolver.resolve(file, catalog, 11).rules()).hasSize(1);
  }

  private static RuleDefinition rule(final String id) {
    return new ClasspathRuleSource()
        .load().rules().stream().filter(rule -> rule.id().equals(id)).findFirst().orElseThrow();
  }
}
