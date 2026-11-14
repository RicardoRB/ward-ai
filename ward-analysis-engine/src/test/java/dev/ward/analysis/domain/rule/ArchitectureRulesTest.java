package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.graph.ProjectFiles;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.rule.ArchitectureConstraint.Kind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The three rule families of {@code ward-architecture.toml} through {@link GraphRules}. */
class ArchitectureRulesTest {
  private static final String DOMAIN = "src/main/java/shop/domain/Order.java";
  private static final String KOTLIN_DOMAIN = "src/main/kotlin/shop/domain/Price.kt";
  private static final String DB = "src/main/java/shop/infrastructure/Db.java";
  private static final String SHARED = "src/main/java/shop/shared/Money.java";
  private static final String APPLICATION = "src/main/java/shop/application/Place.java";
  private static final String REPOSITORY = "src/main/java/shop/repository/OrderStore.java";
  private static final String SERVICE = "src/main/java/shop/service/Orders.java";
  private static final String CONTROLLER = "src/main/java/shop/controller/Orders.java";
  private static final String TEST_DOMAIN = "src/test/java/shop/domain/OrderTest.java";
  private static final int IMPORT_LINE = 4;

  @Test
  void mustNotDependOnReportsTheImportInAJavaFile() {
    final List<GraphMatch> matches =
        dependencies(
            rule("domain-is-pure", dirs("domain"), Kind.MUST_NOT_DEPEND_ON, dirs("infrastructure")),
            edge(DOMAIN, DB));

    assertThat(matches).hasSize(1);
    assertThat(matches.getFirst().file()).isEqualTo(DOMAIN);
    assertThat(matches.getFirst().match().span().startLine()).isEqualTo(IMPORT_LINE);
    assertThat(matches.getFirst().match().message())
        .startsWith("domain-is-pure: ")
        .contains(DB)
        .endsWith(", because the reason");
  }

  @Test
  void mustNotDependOnReportsTheImportInAKotlinFile() {
    final List<GraphMatch> matches =
        dependencies(
            rule("domain-is-pure", dirs("domain"), Kind.MUST_NOT_DEPEND_ON, dirs("infrastructure")),
            edge(KOTLIN_DOMAIN, DB));

    assertThat(matches).extracting(GraphMatch::file).containsExactly(KOTLIN_DOMAIN);
  }

  @Test
  void mayOnlyDependOnAllowsTheSelectionTheListAndNothingElse() {
    final ArchitectureRule rule =
        rule("domain-closed", dirs("domain"), Kind.MAY_ONLY_DEPEND_ON, dirs("shared"));

    final List<GraphMatch> matches =
        dependencies(
            rule, edge(DOMAIN, SHARED), edge(DOMAIN, KOTLIN_DOMAIN), edge(DOMAIN, APPLICATION));

    assertThat(matches).hasSize(1);
    assertThat(matches.getFirst().match().message()).contains(APPLICATION);
  }

  @Test
  void aLibraryImportIsNoEdgeSoItIsNotAFinding() {
    final ArchitectureRule rule =
        rule("domain-closed", dirs("domain"), Kind.MAY_ONLY_DEPEND_ON, dirs("shared"));

    assertThat(dependencies(rule)).isEmpty();
  }

  @Test
  void onlyAccessedByReportsTheImportInTheFileOutsideTheList() {
    final ArchitectureRule rule =
        rule(
            "repositories-behind-services",
            paths("src/**/repository/**"),
            Kind.ONLY_ACCESSED_BY,
            paths("src/**/service/**"));

    final List<GraphMatch> matches =
        dependencies(rule, edge(SERVICE, REPOSITORY), edge(CONTROLLER, REPOSITORY));

    assertThat(matches).extracting(GraphMatch::file).containsExactly(CONTROLLER);
  }

  @Test
  void aTestSourceIsSelectedUnlessExcluded() {
    final ArchitectureSelector excluding =
        new ArchitectureSelector(List.of(), List.of("domain"), List.of("src/test/**"));
    final ArchitectureConstraint constraint =
        new ArchitectureConstraint(Kind.MUST_NOT_DEPEND_ON, dirs("infrastructure"), null);

    assertThat(
            dependencies(
                new ArchitectureRule("open", dirs("domain"), constraint, "why", false),
                edge(TEST_DOMAIN, DB)))
        .hasSize(1);
    assertThat(
            dependencies(
                new ArchitectureRule("closed", excluding, constraint, "why", false),
                edge(TEST_DOMAIN, DB)))
        .isEmpty();
  }

  @Test
  void bothListsOfASelectorMustMatch() {
    final ArchitectureSelector both =
        new ArchitectureSelector(List.of("src/main/**"), List.of("domain"), List.of());

    assertThat(both.matcher().test(DOMAIN)).isTrue();
    assertThat(both.matcher().test(TEST_DOMAIN)).isFalse();
    assertThat(both.matcher().test(DB)).isFalse();
  }

  @Test
  void aSwiftDirectoryNameIgnoresCase() {
    assertThat(dirs("domain").matcher().test("App/Domain/Order.swift")).isTrue();
    assertThat(dirs("domain").matcher().test("App/Domain/order.py")).isFalse();
  }

  @Test
  void aFileNameThatDoesNotMatchIsReportedAtTheFirstLine() {
    final ArchitectureRule rule =
        new ArchitectureRule(
            "controllers-are-named",
            paths("src/**/controller/**"),
            new ArchitectureConstraint(Kind.FILE_NAME, null, "*Controller.*"),
            "the web layer is found by its name",
            false);

    final List<GraphMatch> matches =
        files(
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
            paths("src/**/*Controller.*"),
            new ArchitectureConstraint(Kind.MUST_BE_IN, dirs("controller"), null),
            "the web layer is found by its folder",
            false);
    final String stray = "src/shop/service/OrderController.ts";

    final List<GraphMatch> matches =
        files(
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
        rule("typo", dirs("domian"), Kind.MUST_NOT_DEPEND_ON, dirs("infrastructure"));
    final ArchitectureRule lenient =
        new ArchitectureRule("typo", strict.select(), strict.constraint(), "why", true);
    final ProjectFiles project = project(Map.of(DOMAIN, AnalysisLanguage.JAVA));

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
    final ArchitectureSelector swift = dirs("Domain");
    final ArchitectureRule dependency =
        rule("swift-pure", swift, Kind.MUST_NOT_DEPEND_ON, dirs("Infra"));
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

  @Test
  void theBodyKeepsEveryCharacterOfNamesGlobsAndReasons() {
    final ArchitectureRule rule =
        new ArchitectureRule(
            "odd; name, \"quoted\"\tand\nlines",
            new ArchitectureSelector(List.of("src/{a,b}/**"), List.of("a;b"), List.of("-")),
            new ArchitectureConstraint(Kind.FILE_NAME, null, "*.{ts,js}"),
            "because; reason, with \t tab",
            true);
    final ArchitectureRule other = rule("second", dirs("x"), Kind.ONLY_ACCESSED_BY, paths("y/**"));

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
    assertThatThrownBy(() -> new ArchitectureConstraint(Kind.FILE_NAME, dirs("a"), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ArchitectureConstraint(Kind.MUST_BE_IN, null, "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void everyKindReportsUnderAFamilyThatGraphRulesCompiles() {
    for (final Kind kind : Kind.values()) {
      assertThat(GraphRules.IDS).contains(kind.ruleId());
      final RuleDefinition definition = definition(kind.ruleId(), List.of());
      assertThat(GraphRules.compile(definition).definition().id()).isEqualTo(kind.ruleId());
    }
  }

  private static List<GraphMatch> dependencies(
      final ArchitectureRule rule, final DependencyEdge... edges) {
    return GraphRules.compile(definition(DependencyArchitectureRule.ID, List.of(rule)))
        .matches(new DependencyGraph(List.of(edges)));
  }

  private static List<GraphMatch> files(
      final String id, final ArchitectureRule rule, final Map<String, AnalysisLanguage> sources) {
    return GraphRules.compile(definition(id, List.of(rule)))
        .matches(new DependencyGraph(List.of()), project(sources));
  }

  private static ProjectFiles project(final Map<String, AnalysisLanguage> sources) {
    return new ProjectFiles(sources.keySet(), Set.of(), sources);
  }

  private static RuleDefinition definition(final String id, final List<ArchitectureRule> rules) {
    return new RuleDefinition(
        id,
        Set.of(AnalysisLanguage.JAVA, AnalysisLanguage.PYTHON),
        RuleCapability.SYMBOLS,
        AnalysisSeverity.ERROR,
        ArchitectureRuleBody.encode(rules));
  }

  private static ArchitectureRule rule(
      final String name,
      final ArchitectureSelector select,
      final Kind kind,
      final ArchitectureSelector target) {
    return new ArchitectureRule(
        name, select, new ArchitectureConstraint(kind, target, null), "the reason", false);
  }

  private static ArchitectureSelector dirs(final String... names) {
    return new ArchitectureSelector(List.of(), List.of(names), List.of());
  }

  private static ArchitectureSelector paths(final String... globs) {
    return new ArchitectureSelector(List.of(globs), List.of(), List.of());
  }

  private static DependencyEdge edge(final String from, final String to) {
    final Span span = new Span(IMPORT_LINE, 1, IMPORT_LINE, 2, 40, 41);
    return new DependencyEdge(from, to, new Reference(to, span));
  }
}
