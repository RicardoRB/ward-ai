package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleOption;
import dev.ward.analysis.domain.rule.RuleOptions;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.application.gate.AnalysisRuleLoad;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Every bundled rule is in a category and the standard preset, and every option compiles. */
class BundledRuleOptionsTest {

  private static final AnalysisRuleLoad LOAD = new ClasspathRuleSource().load();
  private static final long NON_DEFAULT = 7;
  private static final String PATTERN = "[a-z]+";

  @Test
  void everyBundledRuleLoads() {
    assertThat(LOAD.configurationErrors()).isEmpty();
    assertThat(LOAD.rules()).isNotEmpty();
  }

  @Test
  void everyBundledRuleHasACategoryAndIsInStandardAndStrict() {
    assertThat(LOAD.rules())
        .allSatisfy(
            rule -> {
              assertThat(rule.category()).as(rule.id()).isNotBlank();
              assertThat(rule.presets())
                  .as(rule.id())
                  .containsAnyOf(RulePreset.STANDARD, RulePreset.STRICT);
              assertThat(RulePreset.STRICT.selects(rule.presets())).as(rule.id()).isTrue();
            });
  }

  @Test
  void everyDeclaredOptionCompilesAtANonDefaultValue() {
    final RuleMatcher matcher = BundledRuleMatchers.create();
    for (final RuleDefinition rule : LOAD.rules()) {
      matcher.compile(rule);
      for (final RuleOption option : rule.options()) {
        final Object value = sample(option);
        assertThat(option.problem(value)).as(rule.id() + " " + option.name()).isEmpty();
        matcher.compile(RuleOptions.apply(rule, Map.of(option.name(), value)));
      }
    }
  }

  private static Object sample(final RuleOption option) {
    return switch (option.type()) {
      case INTEGER -> Math.max(option.min(), 0) + NON_DEFAULT;
      case REGEX -> PATTERN;
      case BOOLEAN -> Boolean.FALSE;
      case STRING_LIST -> List.of("Sample");
      default -> throw new IllegalStateException("no sample for " + option.type());
    };
  }
}
