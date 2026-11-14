package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.rule.ArchitectureConstraint.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The selector, constraint and body of the architecture rules. */
class ArchitectureModelTest {
  private static final String DOMAIN = "src/main/java/shop/domain/Order.java";
  private static final String DB = "src/main/java/shop/infrastructure/Db.java";
  private static final String TEST_DOMAIN = "src/test/java/shop/domain/OrderTest.java";

  @Test
  void bothListsOfASelectorMustMatch() {
    final ArchitectureSelector both =
        new ArchitectureSelector(
            List.of("src/main/**"), List.of(ArchitectureFixtures.DOMAIN_DIR), List.of());

    assertThat(both.matcher().test(DOMAIN)).isTrue();
    assertThat(both.matcher().test(TEST_DOMAIN)).isFalse();
    assertThat(both.matcher().test(DB)).isFalse();
  }

  @Test
  void aSwiftDirectoryNameIgnoresCase() {
    assertThat(
            ArchitectureFixtures.dirs(ArchitectureFixtures.DOMAIN_DIR)
                .matcher()
                .test("App/Domain/Order.swift"))
        .isTrue();
    assertThat(
            ArchitectureFixtures.dirs(ArchitectureFixtures.DOMAIN_DIR)
                .matcher()
                .test("App/Domain/order.py"))
        .isFalse();
  }

  @Test
  void theBodyKeepsEveryCharacterOfNamesGlobsAndReasons() {
    final ArchitectureRule rule =
        new ArchitectureRule(
            "odd; name, \"quoted\"\tand\nlines",
            new ArchitectureSelector(List.of("src/{a,b}/**"), List.of("a;b"), List.of("-")),
            new ArchitectureConstraint(Kind.FILE_NAME, null, "*.{ts,js}"),
            "because; reason, with \t tab",
            true);
    final ArchitectureRule other =
        ArchitectureFixtures.rule(
            "second",
            ArchitectureFixtures.dirs("x"),
            Kind.ONLY_ACCESSED_BY,
            ArchitectureFixtures.paths("y/**"));

    assertThat(ArchitectureRuleBody.decode(ArchitectureRuleBody.encode(List.of(rule, other))))
        .containsExactly(rule, other);
    assertThat(ArchitectureRuleBody.decode("")).isEmpty();
  }

  @Test
  void aMalformedBodyIsRejected() {
    assertThatThrownBy(() -> ArchitectureRuleBody.decode("only\ttwo"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aSelectorNeedsPathsOrDirectoriesAndValidGlobs() {
    assertThatThrownBy(() -> new ArchitectureSelector(List.of(), List.of(), List.of("a/**")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ArchitectureSelector(List.of("{a"), List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ArchitectureSelector(List.of(""), List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aConstraintHasTheValueItsKindNeeds() {
    assertThatThrownBy(
            () -> new ArchitectureConstraint(Kind.FILE_NAME, ArchitectureFixtures.dirs("a"), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ArchitectureConstraint(Kind.MUST_BE_IN, null, "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void everyKindReportsUnderAFamilyThatGraphRulesCompiles() {
    for (final Kind kind : Kind.values()) {
      assertThat(GraphRules.IDS).contains(kind.ruleId());
      final RuleDefinition definition = ArchitectureFixtures.definition(kind.ruleId(), List.of());
      assertThat(GraphRules.compile(definition).definition().id()).isEqualTo(kind.ruleId());
    }
  }
}
