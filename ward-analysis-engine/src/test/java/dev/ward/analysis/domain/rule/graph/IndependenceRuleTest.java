package dev.ward.analysis.domain.rule.graph;

import static dev.ward.analysis.domain.rule.graph.GraphRuleFixtures.definition;
import static dev.ward.analysis.domain.rule.graph.GraphRuleFixtures.edge;
import static dev.ward.analysis.domain.rule.graph.GraphRuleFixtures.graph;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class IndependenceRuleTest {

  private static final int IMPORT_LINE = 7;
  private static final List<List<String>> TABLE = List.of(List.of("shop/orders", "shop/billing"));

  private static IndependenceRule rule() {
    return new IndependenceRule(definition(IndependenceRule.ID, "shop/orders,shop/billing"), TABLE);
  }

  @Test
  void reportsAnImportBetweenTwoListedModulesAtTheImport() {
    final List<GraphMatch> matches =
        rule()
            .matches(
                graph(edge("shop/orders/Order.java", "shop/billing/Invoice.java", IMPORT_LINE)));

    assertThat(matches).hasSize(1);
    assertThat(matches.getFirst().file()).isEqualTo("shop/orders/Order.java");
    assertThat(matches.getFirst().match().span().startLine()).isEqualTo(IMPORT_LINE);
    assertThat(matches.getFirst().match().message())
        .isEqualTo("shop/orders must not depend on shop/billing");
    assertThat(matches.getFirst().match().path()).isEmpty();
  }

  @Test
  void allowsSharedCodeOutsideTheListedModules() {
    final List<GraphMatch> matches =
        rule()
            .matches(
                graph(
                    edge("shop/orders/Order.java", "shop/common/Money.java", 1),
                    edge("shop/billing/Invoice.java", "shop/common/Money.java", 1)));

    assertThat(matches).isEmpty();
  }

  @Test
  void matchesModuleDirectoriesNotNamePrefixes() {
    final List<GraphMatch> matches =
        rule().matches(graph(edge("shop/ordersLegacy/Old.java", "shop/billing/Invoice.java", 1)));

    assertThat(matches).isEmpty();
  }

  @Test
  void usesTheLongestListedModuleThatContainsTheFile() {
    final IndependenceRule rule =
        new IndependenceRule(
            definition(IndependenceRule.ID, "shop,shop/orders/core,shop/billing"),
            List.of(List.of("shop", "shop/orders/core", "shop/billing")));

    final List<GraphMatch> matches =
        rule.matches(graph(edge("shop/orders/core/A.java", "shop/billing/B.java", 1)));

    assertThat(matches).hasSize(1);
    assertThat(matches.getFirst().match().message())
        .isEqualTo("shop/orders/core must not depend on shop/billing");
  }
}
