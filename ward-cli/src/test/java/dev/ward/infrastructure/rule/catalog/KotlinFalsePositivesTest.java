package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The Kotlin false positives that the corpora labelled and that were fixed (story #595). */
class KotlinFalsePositivesTest {
  private static final Path FIXTURES = Path.of("src/test/resources/rules/kotlin");
  private static final TypeFacts NO_FACTS = FixtureTypeFacts.of(Optional.empty());

  static Stream<Arguments> fixedRules() {
    return Stream.of(
        Arguments.of("property-name", "backticks"),
        Arguments.of("function-name", "backticks"),
        Arguments.of("function-name", "factory"),
        Arguments.of("spread-operator", "own-vararg"),
        Arguments.of("overload-methods-declaration-order", "extension"),
        Arguments.of("utility-class-with-public-constructor", "actual")
    );
  }

  @DisplayName("a labelled false positive is fixed")
  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("fixedRules")
  void aLabelledFalsePositiveIsFixed(final String rule, final String construct)
      throws IOException {
    // Given a rule listed in the story and a fixture written from scratch with the construct the
    // corpus showed
    final RuleDefinition definition =
        CatalogRuleDefinitions.all().stream()
            .filter(candidate -> candidate.id().equals("ward:kotlin/" + rule))
            .findFirst()
            .orElseThrow();
    final Path passing = FIXTURES.resolve(rule).resolve("passing-" + construct + ".kt");
    final Path violating = FIXTURES.resolve(rule).resolve("violating-" + construct + ".kt");
    final RuleFixtureRunner runner = new RuleFixtureRunner();

    // When the rule runs on the fixture
    final int onPassing =
        runner.run(
            definition,
            passing.getFileName().toString(),
            Files.readString(passing),
            NO_FACTS
        )
            .size();
    final List<String> expected =
        Files.readAllLines(FIXTURES.resolve(rule).resolve("expected-" + construct + ".txt"));
    final int onViolating =
        runner
            .run(
                definition,
                violating.getFileName().toString(),
                Files.readString(violating),
                NO_FACTS
            )
            .size();

    // Then it reports no finding there, and it still reports its violating fixture
    assertThat(onPassing).isZero();
    assertThat(onViolating).isEqualTo(expected.size());
  }
}
