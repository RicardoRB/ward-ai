package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ArchitectureConstraint.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The dependency constraints of {@code ward-architecture.toml} through {@link GraphRules}. */
class ArchitectureDependencyRuleTest {
  private static final String DOMAIN = "src/main/java/shop/domain/Order.java";
  private static final String KOTLIN_DOMAIN = "src/main/kotlin/shop/domain/Price.kt";
  private static final String DB = "src/main/java/shop/infrastructure/Db.java";
  private static final String SHARED = "src/main/java/shop/shared/Money.java";
  private static final String APPLICATION = "src/main/java/shop/application/Place.java";
  private static final String REPOSITORY = "src/main/java/shop/repository/OrderStore.java";
  private static final String SERVICE = "src/main/java/shop/service/Orders.java";
  private static final String CONTROLLER = "src/main/java/shop/controller/Orders.java";
  private static final String TEST_DOMAIN = "src/test/java/shop/domain/OrderTest.java";

  @Test
  void mustNotDependOnReportsTheImportInAJavaFile() {
    final List<GraphMatch> matches =
        ArchitectureFixtures.dependencies(
            ArchitectureFixtures.rule(
                "domain-is-pure",
                ArchitectureFixtures.dirs(ArchitectureFixtures.DOMAIN_DIR),
                Kind.MUST_NOT_DEPEND_ON,
                ArchitectureFixtures.dirs("infrastructure")),
            ArchitectureFixtures.edge(DOMAIN, DB));

    assertThat(matches).hasSize(1);
    assertThat(matches.getFirst().file()).isEqualTo(DOMAIN);
    assertThat(matches.getFirst().match().span().startLine())
        .isEqualTo(ArchitectureFixtures.IMPORT_LINE);
    assertThat(matches.getFirst().match().message())
        .startsWith("domain-is-pure: ")
        .contains(DB)
        .endsWith(", because the reason");
  }

  @Test
  void mustNotDependOnReportsTheImportInAKotlinFile() {
    final List<GraphMatch> matches =
        ArchitectureFixtures.dependencies(
            ArchitectureFixtures.rule(
                "domain-is-pure",
                ArchitectureFixtures.dirs(ArchitectureFixtures.DOMAIN_DIR),
                Kind.MUST_NOT_DEPEND_ON,
                ArchitectureFixtures.dirs("infrastructure")),
            ArchitectureFixtures.edge(KOTLIN_DOMAIN, DB));

    assertThat(matches).extracting(GraphMatch::file).containsExactly(KOTLIN_DOMAIN);
  }

  @Test
  void mayOnlyDependOnAllowsTheSelectionTheListAndNothingElse() {
    final ArchitectureRule rule =
        ArchitectureFixtures.rule(
            "domain-closed",
            ArchitectureFixtures.dirs(ArchitectureFixtures.DOMAIN_DIR),
            Kind.MAY_ONLY_DEPEND_ON,
            ArchitectureFixtures.dirs("shared"));

    final List<GraphMatch> matches =
        ArchitectureFixtures.dependencies(
            rule,
            ArchitectureFixtures.edge(DOMAIN, SHARED),
            ArchitectureFixtures.edge(DOMAIN, KOTLIN_DOMAIN),
            ArchitectureFixtures.edge(DOMAIN, APPLICATION));

    assertThat(matches).hasSize(1);
    assertThat(matches.getFirst().match().message()).contains(APPLICATION);
  }

  @Test
  void aLibraryImportIsNoEdgeSoItIsNotAFinding() {
    final ArchitectureRule rule =
        ArchitectureFixtures.rule(
            "domain-closed",
            ArchitectureFixtures.dirs(ArchitectureFixtures.DOMAIN_DIR),
            Kind.MAY_ONLY_DEPEND_ON,
            ArchitectureFixtures.dirs("shared"));

    assertThat(ArchitectureFixtures.dependencies(rule)).isEmpty();
  }

  @Test
  void onlyAccessedByReportsTheImportInTheFileOutsideTheList() {
    final ArchitectureRule rule =
        ArchitectureFixtures.rule(
            "repositories-behind-services",
            ArchitectureFixtures.paths("src/**/repository/**"),
            Kind.ONLY_ACCESSED_BY,
            ArchitectureFixtures.paths("src/**/service/**"));

    final List<GraphMatch> matches =
        ArchitectureFixtures.dependencies(
            rule,
            ArchitectureFixtures.edge(SERVICE, REPOSITORY),
            ArchitectureFixtures.edge(CONTROLLER, REPOSITORY));

    assertThat(matches).extracting(GraphMatch::file).containsExactly(CONTROLLER);
  }

  @Test
  void aTestSourceIsSelectedUnlessExcluded() {
    final ArchitectureSelector excluding =
        new ArchitectureSelector(
            List.of(), List.of(ArchitectureFixtures.DOMAIN_DIR), List.of("src/test/**"));
    final ArchitectureConstraint constraint =
        new ArchitectureConstraint(
            Kind.MUST_NOT_DEPEND_ON, ArchitectureFixtures.dirs("infrastructure"), null);

    assertThat(
            ArchitectureFixtures.dependencies(
                new ArchitectureRule(
                    "open",
                    ArchitectureFixtures.dirs(ArchitectureFixtures.DOMAIN_DIR),
                    constraint,
                    "why",
                    false),
                ArchitectureFixtures.edge(TEST_DOMAIN, DB)))
        .hasSize(1);
    assertThat(
            ArchitectureFixtures.dependencies(
                new ArchitectureRule("closed", excluding, constraint, "why", false),
                ArchitectureFixtures.edge(TEST_DOMAIN, DB)))
        .isEmpty();
  }
}
