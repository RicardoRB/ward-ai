package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleOption;
import dev.ward.analysis.domain.rule.RuleOptionType;
import dev.ward.analysis.domain.rule.RuleOptions;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The options the example rules declare are the ones their check kind lists, and they compile. */
class ExampleOptionsTest {

  private static final long SAMPLE_NUMBER = 5;

  @Test
  void everyDeclaredOptionIsAUserOptionOfTheKindWithTheSameType() {
    for (final RuleDefinition rule : rules()) {
      final List<CheckOption> listed = CheckOptions.of(rule.body());
      for (final RuleOption option : rule.options()) {
        assertThat(listed)
            .as(rule.id() + " " + option.name())
            .anySatisfy(
                kind -> {
                  assertThat(kind.name()).isEqualTo(option.name());
                  assertThat(kind.type()).isEqualTo(option.type());
                });
      }
    }
  }

  @Test
  void everyDeclaredOptionCompilesAtAnotherValue() {
    final RuleMatcher matcher = new DeclarativeRuleMatcher();
    for (final RuleDefinition rule : rules()) {
      for (final RuleOption option : rule.options()) {
        final Object value = sample(option.type());
        assertThat(option.problem(value)).as(rule.id() + " " + option.name()).isEmpty();
        assertThat(matcher.compile(RuleOptions.apply(rule, Map.of(option.name(), value))))
            .isNotNull();
      }
    }
  }

  @Test
  void everyKindWithOptionsIsDeclaredByAnExampleRule() {
    final List<RuleDefinition> rules = rules();
    for (final GenericKind kind : GenericKind.values()) {
      if (!kind.options().isEmpty()) {
        assertThat(rules)
            .as(kind.id())
            .anyMatch(
                rule ->
                    rule.body().contains("check = \"" + kind.id() + "\"")
                        && !rule.options().isEmpty());
      }
    }
  }

  private static List<RuleDefinition> rules() {
    return Stream.concat(ExampleRuns.rules("java").stream(), ExampleRuns.rules("python").stream())
        .toList();
  }

  private static Object sample(final RuleOptionType type) {
    return switch (type) {
      case INTEGER -> SAMPLE_NUMBER;
      case REGEX -> "[a-z]+";
      case STRING_LIST -> List.of("x");
      default -> throw new IllegalStateException("no sample for " + type);
    };
  }
}
