package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.graph.ProjectFiles;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.ArchitectureConstraint.Kind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The naming and location constraints, and the rules that cannot be evaluated. */
class ArchitectureFileRulesTest {
  private static final String DOMAIN = "src/main/java/shop/domain/Order.java";
  private static final String CONTROLLER = "src/main/java/shop/controller/Orders.java";

  @Test
  void aFileNameThatDoesNotMatchIsReportedAtTheFirstLine() {
    final ArchitectureRule rule =
        new ArchitectureRule(
            "controllers-are-named",
            ArchitectureFixtures.paths("src/**/controller/**"),
            new ArchitectureConstraint(Kind.FILE_NAME, null, "*Controller.*"),
            "the web layer is found by its name",
            false);

    final List<GraphMatch> matches =
        ArchitectureFixtures.files(
            NamingArchitectureRule.ID,
            rule,
            Map.of(
                CONTROLLER,
                AnalysisLanguage.JAVA,
                "src/main/java/shop/controller/OrderController.java",
                AnalysisLanguage.JAVA));

    assertThat(matches).extracting(GraphMatch::file).containsExactly(CONTROLLER);
    assertThat(matches.getFirst().match().span().startLine()).isEqualTo(1);
    assertThat(matches.getFirst().match().message())
        .startsWith("controllers-are-named: ")
        .endsWith(", because the web layer is found by its name");
  }

  @Test
  void aFileOutsideItsExpectedLocationIsReported() {
    final ArchitectureRule rule =
        new ArchitectureRule(
            "controllers-in-folder",
            ArchitectureFixtures.paths("src/**/*Controller.*"),
            new ArchitectureConstraint(
                Kind.MUST_BE_IN, ArchitectureFixtures.dirs("controller"), null),
            "the web layer is found by its folder",
            false);
    final String stray = "src/shop/service/OrderController.ts";

    final List<GraphMatch> matches =
        ArchitectureFixtures.files(
            LocationArchitectureRule.ID,
            rule,
            Map.of(
                stray,
                AnalysisLanguage.TYPESCRIPT,
                "src/shop/controller/UserController.ts",
                AnalysisLanguage.TYPESCRIPT));

    assertThat(matches).extracting(GraphMatch::file).containsExactly(stray);
  }

  @Test
  void aRuleThatSelectsNoFileIsAnErrorUnlessItAllowsIt() {
    final ArchitectureRule strict =
        ArchitectureFixtures.rule(
            "typo",
            ArchitectureFixtures.dirs("domian"),
            Kind.MUST_NOT_DEPEND_ON,
            ArchitectureFixtures.dirs("infrastructure"));
    final ArchitectureRule lenient =
        new ArchitectureRule("typo", strict.select(), strict.constraint(), "why", true);
    final ProjectFiles project =
        ArchitectureFixtures.project(Map.of(DOMAIN, AnalysisLanguage.JAVA));

    assertThat(ArchitectureRuleCheck.errors(List.of(strict), project))
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.ruleId()).isEqualTo(DependencyArchitectureRule.ID);
              assertThat(error.reason()).contains("'typo'", "selects no file");
            });
    assertThat(ArchitectureRuleCheck.errors(List.of(lenient), project)).isEmpty();
  }

  @Test
  void aDependencyRuleOverAFileWithoutASummaryIsAnErrorButANamingRuleIsNot() {
    final ArchitectureSelector swift = ArchitectureFixtures.dirs("Domain");
    final ArchitectureRule dependency =
        ArchitectureFixtures.rule(
            "swift-pure", swift, Kind.MUST_NOT_DEPEND_ON, ArchitectureFixtures.dirs("Infra"));
    final ArchitectureRule naming =
        new ArchitectureRule(
            "swift-named",
            swift,
            new ArchitectureConstraint(Kind.FILE_NAME, null, "*.swift"),
            "why",
            false);
    final ProjectFiles project =
        new ProjectFiles(
            Set.of(), Set.of(), Map.of("App/Domain/Order.swift", AnalysisLanguage.SWIFT));

    assertThat(ArchitectureRuleCheck.errors(List.of(dependency), project))
        .singleElement()
        .satisfies(
            error ->
                assertThat(error.reason())
                    .contains("'swift-pure'", "SWIFT", "App/Domain/Order.swift"));
    assertThat(ArchitectureRuleCheck.errors(List.of(naming), project)).isEmpty();
  }
}
