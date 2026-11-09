package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeControlFlow;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class BiomeUnreachableSuperCheckTest {

  private static final BiomeUnreachableSuperCheck CHECK = new BiomeUnreachableSuperCheck("message");

  @Test
  void reportsADerivedConstructorThatReturnsWithoutSuper() {
    assertThat(reported("class A extends B {\n  constructor() {}\n}\n")).hasSize(1);
  }

  @Test
  void reportsAConstructorThatReadsThisBeforeSuper() {
    assertThat(
            reported(
                "class A extends B {\n  constructor() {\n    this.x = 1;\n    super();\n  }\n}\n"))
        .hasSize(1);
  }

  @Test
  void keepsAConstructorThatAlwaysCallsSuper() {
    assertThat(
            reported(
                "class A extends B {\n  constructor(c) {\n    super();\n    this.x = c;\n  }\n}\n"))
        .isEmpty();
  }

  @Test
  void reportsAConstructorThatCallsSuperTwiceOnAPath() {
    assertThat(
            reported(
                "class A extends B {\n  constructor() {\n    super();\n    super();\n  }\n}\n"))
        .hasSize(1);
  }

  @Test
  void ignoresAClassWithoutExtends() {
    assertThat(reported("class A {\n  constructor() {}\n}\n")).isEmpty();
  }

  private static List<String> reported(final String source) {
    final SourceUnit unit = new SourceUnit("m", AnalysisLanguage.TYPESCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    final FlowView flow = new FlowView(new NodeControlFlow().lower(unit, root).graphs());
    return CHECK.matches(root, flow).stream()
        .map(RuleMatch::span)
        .map(span -> source.substring(span.startOffset(), span.endOffset()))
        .toList();
  }
}
