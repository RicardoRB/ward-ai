package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The Java rules whose false positives the corpus run of #564 labelled (task #627): each one's
 * {@code passing.java} holds, written from scratch, the construct the corpus showed, and its {@code
 * violating.java} still reports.
 */
class JavaCorpusFalsePositivesTest {
  /** The rules fixed for #627. */
  private static final List<String> FIXED =
      List.of(
          "assert-statement-in-test",
          "avoid-accessibility-alteration",
          "bshift-wrong-add-priority",
          "clone-method-must-be-public",
          "clone-method-must-implement-cloneable",
          "cn-idiom-no-super-call",
          "cn-implements-clone-but-not-cloneable",
          "control-statement-braces",
          "ei-expose-rep",
          "eq-always-true",
          "eq-overriding-equals-not-symmetric",
          "iju-assert-method-invoked-from-run-method",
          "int-vacuous-comparison",
          "method-returns-internal-array",
          "ms-expose-rep",
          "non-thread-safe-singleton",
          "proper-clone-implementation",
          "ri-redundant-interfaces",
          "se-transient-field-of-nonserializable-class",
          "simplifiable-test-assertion",
          "single-line-javadoc",
          "suspicious-equals-method-name",
          "unnecessary-interface-declaration",
          "uwf-field-not-initialized-in-constructor",
          "variable-can-be-inlined"
      );

  /**
   * The fixed rules that keep a labelled false positive, with the reason (also posted on #627): the
   * superclass of that class is a class of the same file that extends {@code Thread}.
   */
  private static final List<String> KEPT_IN_PART = List.of("eq-overriding-equals-not-symmetric");

  /** The rules that look at test sources only: their violating fixture is quiet in main code. */
  private static final List<String> ON_MAIN_CODE = List.of("assert-statement-in-test");

  private static final int LABEL_FIELDS = 4;
  private static final int KIND = 2;
  private static final Path LABELS = Path.of("src/test/resources/benchmark/java-corpus-labels.txt");
  private static final Map<String, RuleDefinition> RULES =
      CatalogRuleDefinitions.all().stream()
          .collect(Collectors.toMap(RuleDefinition::id, rule -> rule));

  static Stream<String> fixed() {
    return FIXED.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixed")
  @DisplayName("a labelled false positive is fixed")
  void aLabelledFalsePositiveIsFixed(final String name) throws IOException {
    // Given a rule listed below and a fixture written from scratch with the construct the corpus
    // showed
    final RuleDefinition rule = RULES.get("ward:java/" + name);
    final RuleFixtureSet fixtures =
        new RuleFixtureSet(
            AnalysisLanguage.JAVA,
            name,
            RuleFixtureSet.root().resolve("java").resolve(name)
        );
    final TypeFacts typeFacts = FixtureTypeFacts.of(fixtures.typeFacts());

    // When the rule runs on the fixture
    final List<AnalysisFinding> onPassing = run(rule, fixtures.passing(""), typeFacts);
    final List<AnalysisFinding> onViolating = run(rule, fixtures.violating(""), typeFacts);

    // Then it reports no finding there
    assertThat(onPassing).isEmpty();
    if (ON_MAIN_CODE.contains(name)) {
      assertThat(runOnMainCode(rule, fixtures.violating(""), typeFacts)).isEmpty();
    }
    // And it still reports its violating fixture
    assertThat(onViolating).isNotEmpty();
  }

  @Test
  @DisplayName("the corpus labels are clean")
  void theCorpusLabelsAreClean() throws IOException {
    // Given the regenerated reports and labels of #564
    final List<String> labels = Files.readAllLines(LABELS);

    // When the corpus smoke tests run, as their committed labels record
    final List<String> falseOnes =
        labels.stream()
            .filter(line -> !line.startsWith("#"))
            .map(line -> line.split(" \\| ", LABEL_FIELDS))
            .filter(parts -> parts.length == LABEL_FIELDS && "fp".equals(parts[KIND]))
            .map(parts -> parts[0] + " " + parts[1])
            .filter(
                place ->
                    FIXED.stream()
                        .filter(name -> !KEPT_IN_PART.contains(name))
                        .anyMatch(name -> place.startsWith("ward:java/" + name + " "))
            )
            .toList();

    // Then no finding of a fixed rule is labelled false
    assertThat(falseOnes).isEmpty();
  }

  private static List<AnalysisFinding> runOnMainCode(
      final RuleDefinition rule,
      final Path file,
      final TypeFacts typeFacts
  ) throws IOException {
    return new RuleFixtureRunner()
        .run(
            rule,
            "src/main/java/Checks.java",
            Files.readString(file),
            typeFacts,
            FixtureProjects.fixtureProject(file, rule)
        );
  }

  private static List<AnalysisFinding> run(
      final RuleDefinition rule,
      final Path file,
      final TypeFacts typeFacts
  ) throws IOException {
    return new RuleFixtureRunner()
        .run(
            rule,
            unitName(file),
            Files.readString(file),
            typeFacts,
            FixtureProjects.fixtureProject(file, rule)
        );
  }

  private static String unitName(final Path file) throws IOException {
    final Path named = file.resolveSibling("filename.txt");
    return Files.isRegularFile(named)
        ? Files.readString(named).strip()
        : file.getFileName().toString();
  }
}
