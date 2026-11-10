package dev.ward.analysis.domain.rule;

import static dev.ward.analysis.domain.rule.LayerRuleFixtures.edge;
import static dev.ward.analysis.domain.rule.LayerRuleFixtures.match;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.DependencyEdge;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LayerDependencyRuleTest {

  private static final LayerBoundary DOMAIN_NO_INFRA =
      new LayerBoundary(List.of("domain"), List.of("infrastructure"));

  @Test
  void reportsEdgeFromFromLayerToForbiddenLayerAtReference() {
    final DependencyEdge edge =
        edge("shop/domain/Order.java", "shop/infrastructure/Db.java", 3, 10);
    final List<RuleMatch> matches = match(List.of(DOMAIN_NO_INFRA), edge);

    assertThat(matches).hasSize(1);
    assertThat(matches.getFirst().span()).isEqualTo(edge.reference().span());
    assertThat(matches.getFirst().message())
        .isEqualTo("Layer domain must not depend on infrastructure: shop/infrastructure/Db.java");
  }

  @Test
  void allowsTheOppositeDirection() {
    final DependencyEdge edge = edge("shop/infrastructure/Db.java", "shop/domain/Order.java", 1, 1);

    assertThat(match(List.of(DOMAIN_NO_INFRA), edge)).isEmpty();
  }

  @Test
  void matchesLayerAnywhereInTheDirectoryPathButNotTheFileName() {
    final DependencyEdge nested =
        edge("src/main/java/shop/orders/domain/Order.java", "x/infrastructure/Db.java", 1, 1);
    final DependencyEdge fileNamedAsLayer = edge("pkg/domain.ts", "pkg/infrastructure/Db.ts", 1, 1);

    assertThat(match(List.of(DOMAIN_NO_INFRA), nested)).hasSize(1);
    assertThat(match(List.of(DOMAIN_NO_INFRA), fileNamedAsLayer)).isEmpty();
  }

  @Test
  void comparesSwiftFolderNamesIgnoringCaseAndOtherFolderNamesExactly() {
    final DependencyEdge swift =
        edge("App/Domain/Order.swift", "App/Infrastructure/Db.swift", 2, 1);
    final DependencyEdge typeScript = edge("App/Domain/Order.ts", "App/Infrastructure/Db.ts", 2, 1);

    assertThat(match(List.of(DOMAIN_NO_INFRA), swift)).hasSize(1);
    assertThat(match(List.of(DOMAIN_NO_INFRA), typeScript)).isEmpty();
  }

  @Test
  void reportsOncePerBoundaryItBreaks() {
    final DependencyEdge edge = edge("a/domain/X.py", "a/infrastructure/Y.py", 1, 1);
    final List<RuleMatch> matches =
        match(
            List.of(
                DOMAIN_NO_INFRA,
                new LayerBoundary(List.of("domain"), List.of("infrastructure", "adapter"))),
            edge);

    assertThat(matches).hasSize(2);
  }

  @Test
  void ignoresJavaAndSwiftTestSources() {
    final DependencyEdge testSource =
        edge("src/test/java/shop/domain/OrderTest.java", "shop/infrastructure/Db.java", 1, 1);
    final DependencyEdge swiftTest = edge("Tests/domain/T.swift", "infrastructure/Db.swift", 1, 1);

    assertThat(match(List.of(DOMAIN_NO_INFRA), testSource)).isEmpty();
    assertThat(match(List.of(DOMAIN_NO_INFRA), swiftTest)).isEmpty();
  }

  @Test
  void reportsNothingWithoutAView() {
    assertThat(LayerRuleFixtures.rule(List.of(DOMAIN_NO_INFRA)).matches(LayerRuleFixtures.root()))
        .isEmpty();
  }

  @Test
  void roundTripsBoundariesThroughTheRuleBody() {
    final List<LayerBoundary> boundaries =
        List.of(
            DOMAIN_NO_INFRA,
            new LayerBoundary(List.of("controller", "api"), List.of("repository")));

    assertThat(LayerRuleBody.decode(LayerRuleBody.encode(boundaries))).isEqualTo(boundaries);
  }

  @Test
  void rejectsMalformedBodyAndInvalidBoundaries() {
    assertThatThrownBy(() -> LayerRuleBody.decode("domain-infrastructure"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LayerBoundary(List.of(), List.of("infra")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LayerBoundary(List.of("do/main"), List.of("infra")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsCrossLanguageIdOnlyForSeveralLanguages() {
    final RuleDefinition multi =
        new RuleDefinition(
            LayerDependencyRule.ID,
            Set.of(AnalysisLanguage.JAVA, AnalysisLanguage.TYPESCRIPT),
            RuleCapability.SYMBOLS,
            AnalysisSeverity.ERROR,
            "");

    assertThat(multi.covers(AnalysisLanguage.JAVA)).isTrue();
    assertThat(multi.covers(AnalysisLanguage.PYTHON)).isFalse();
    assertThatThrownBy(
            () ->
                new RuleDefinition(
                    "ward:architecture/layers",
                    AnalysisLanguage.JAVA,
                    RuleCapability.SYMBOLS,
                    AnalysisSeverity.ERROR,
                    ""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new RuleDefinition(
                    "ward:java/layers",
                    Set.of(AnalysisLanguage.JAVA, AnalysisLanguage.PYTHON),
                    RuleCapability.SYMBOLS,
                    AnalysisSeverity.ERROR,
                    ""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
