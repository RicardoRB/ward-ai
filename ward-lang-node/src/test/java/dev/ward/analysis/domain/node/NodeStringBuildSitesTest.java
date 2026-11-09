package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The string-building flow sites that the TypeScript and JavaScript lowering records. */
class NodeStringBuildSitesTest {

  private static final int ONE_VALUE = 1;

  /** Scenario: a {@code +} chain with a literal operand is one site named by its literal text. */
  @Test
  void plusChainWithLiteralIsOneSiteNamedByItsLiteralParts() {
    final List<FlowSite> found =
        builds("function h(event) {\n  return '<p>' + event.body + '</p>';\n}\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).name()).isEqualTo("<p></p>");
    assertThat(found.get(0).arguments()).hasSize(ONE_VALUE);
  }

  /** Scenario: a template literal with a substitution is one site with the substitutions. */
  @Test
  void templateWithSubstitutionIsOneSite() {
    final List<FlowSite> found =
        builds("function h(event) {\n  return `<div>${event.name}</div>`;\n}\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).name()).isEqualTo("<div></div>");
    assertThat(found.get(0).arguments()).hasSize(ONE_VALUE);
  }

  /** Scenario: concat on a string literal receiver is a site named by the literal. */
  @Test
  void concatOnLiteralReceiverIsASite() {
    final List<FlowSite> found =
        builds("function h(event) {\n  return '<p>'.concat(event.body);\n}\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).name()).isEqualTo("<p>");
    assertThat(found.get(0).arguments()).hasSize(ONE_VALUE);
  }

  /** Scenario: concat on a value receiver stays a call, not a string-building site. */
  @Test
  void concatOnValueReceiverIsNotASite() {
    assertThat(builds("function h(event) {\n  return items.concat(event.records);\n}\n")).isEmpty();
  }

  /** Scenario: numeric addition has no string literal, so it is not a site. */
  @Test
  void numericAdditionIsNotASite() {
    assertThat(builds("function h(a, b) {\n  return a + b;\n}\n")).isEmpty();
  }

  /** Scenario: a chain of value operands has no literal, so it is not a site. */
  @Test
  void valueChainWithoutLiteralIsNotASite() {
    assertThat(builds("function h(event) {\n  return event.a + event.b;\n}\n")).isEmpty();
  }

  /** Scenario: a template without substitutions is a plain literal, not a site. */
  @Test
  void plainTemplateIsNotASite() {
    assertThat(builds("function h() {\n  return `<p>plain</p>`;\n}\n")).isEmpty();
  }

  /**
   * Scenario: a literal chain with a non-HTML name is still a site; the rule's pattern filters it.
   */
  @Test
  void plainLiteralChainIsASiteNamedByItsText() {
    final List<FlowSite> found = builds("function h(event) {\n  return 'id: ' + event.id;\n}\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).name()).isEqualTo("id: ");
  }

  private static List<FlowSite> builds(final String source) {
    return NodeFlowFixtures.lower(source).graphs().stream()
        .flatMap(graph -> graph.blocks().stream())
        .map(BasicBlock::sites)
        .flatMap(List::stream)
        .filter(site -> site.kind() == FlowSiteKind.STRING_BUILD)
        .toList();
  }
}
