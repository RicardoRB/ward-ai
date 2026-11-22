package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Contracts of the presets over the whole shipped catalog (#698): no preset enables two rules that
 * contradict each other (#685, #695), and a rule written for a platform is not part of a preset of
 * a language that runs on every platform (#686).
 */
class PresetContractTest {
  private static final Path CONFLICTS = Path.of(
      "src/main/resources/dev/ward/application/gate/rule-conflicts.txt"
  );
  private static final int FIELDS = 3;
  private static final String NO_PREFIX = "no-";
  private static final Set<AnalysisLanguage> PLATFORM_FREE =
      Set.of(AnalysisLanguage.JAVA, AnalysisLanguage.KOTLIN);

  @Test
  void noPresetEnablesTwoContradictoryRules() throws IOException {
    // Given the shipped rules and the committed pairs that contradict each other
    final Map<String, RuleDefinition> rules =
        CatalogRuleDefinitions.shipped().stream()
            .collect(Collectors.toMap(RuleDefinition::id, Function.identity(), (a, b) -> a));
    final List<String[]> pairs = new ArrayList<>(committedPairs());
    rules.keySet().stream()
        .filter(id -> id.substring(id.indexOf('/') + 1).startsWith(NO_PREFIX))
        .map(id -> id.replace("/" + NO_PREFIX, "/"))
        .filter(rules::containsKey)
        .forEach(
            positive -> pairs.add(new String[] {positive, positive.replace("/", "/" + NO_PREFIX)})
        );

    // When every preset is checked for both rules of a pair
    final List<String> conflicts = new ArrayList<>();
    for (final RulePreset preset : List.of(RulePreset.STANDARD, RulePreset.STRICT)) {
      for (final String[] pair : pairs) {
        if (selects(preset, rules.get(pair[0])) && selects(preset, rules.get(pair[1]))) {
          conflicts.add(preset.key() + ": " + pair[0] + " vs " + pair[1]);
        }
      }
    }

    // Then none is enabled together
    assertThat(conflicts).isEmpty();
  }

  @Test
  void theCommittedPairsNameShippedRules() throws IOException {
    final Set<String> ids =
        CatalogRuleDefinitions.shipped().stream()
            .map(RuleDefinition::id)
            .collect(Collectors.toSet());

    assertThat(committedPairs())
        .allSatisfy(pair -> assertThat(ids).contains(pair[0], pair[1]));
  }

  @Test
  void aPlatformRuleIsNotInAPresetOfAPlatformFreeLanguage() {
    final List<String> platform =
        CatalogRuleDefinitions.shipped().stream()
            .filter(rule -> PLATFORM_FREE.stream().anyMatch(rule::covers))
            .filter(rule -> !rule.presets().isEmpty())
            .filter(PresetContractTest::namesAndroidWithoutAndroidTypes)
            .map(RuleDefinition::id)
            .sorted()
            .toList();

    assertThat(platform).isEmpty();
  }

  /**
   * A rule gated by an {@code android.} type or a Dagger Android injector never reports on code
   * that has none.
   */
  private static boolean namesAndroidWithoutAndroidTypes(final RuleDefinition rule) {
    final String body =
        rule.body().toLowerCase(Locale.ROOT).replaceAll("android(?:support)?injection", "");
    return body.contains("android") && !body.contains("android.") && !body.contains("androidx.");
  }

  private static boolean selects(final RulePreset preset, final RuleDefinition rule) {
    return preset.selects(rule.presets());
  }

  private static List<String[]> committedPairs() throws IOException {
    return Files.readAllLines(CONFLICTS, StandardCharsets.UTF_8).stream()
        .filter(line -> !line.startsWith("#") && !line.isBlank())
        .map(line -> line.split(" \\| ", FIELDS))
        .toList();
  }
}
