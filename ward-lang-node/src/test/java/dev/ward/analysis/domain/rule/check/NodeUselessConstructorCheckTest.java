package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the TypeScript useless-constructor check on parsed sources. */
class NodeUselessConstructorCheckTest {

  @Test
  void typedForwardingConstructorIsReported() {
    assertThat(run("class A extends B { constructor(v: number) { super(v); } }")).hasSize(1);
  }

  @Test
  void restForwardingConstructorIsReported() {
    assertThat(run("class A extends B { constructor(...r: number[]) { super(...r); } }"))
        .hasSize(1);
  }

  @Test
  void privateConstructorIsNotReported() {
    assertThat(run("class A extends B { private constructor(v: number) { super(v); } }")).isEmpty();
  }

  @Test
  void parameterPropertyIsNotReported() {
    assertThat(run("class A extends B { constructor(private v: number) { super(v); } }")).isEmpty();
  }

  @Test
  void decoratedParameterIsNotReported() {
    assertThat(run("class A extends B { constructor(@Inject() v: number) { super(v); } }"))
        .isEmpty();
  }

  @Test
  void overloadSignatureWithoutBodyIsNotReported() {
    assertThat(run("class A extends B { constructor(v: number); }")).isEmpty();
  }

  @Test
  void bodyWithMoreThanTheSuperCallIsNotReported() {
    assertThat(run("class A extends B { constructor(v: number) { super(v); this.v = v; } }"))
        .isEmpty();
  }

  @Test
  void defaultValueParameterIsNotReported() {
    assertThat(run("class A extends B { constructor(v: number = 1) { super(v); } }")).isEmpty();
  }

  private static List<RuleMatch> run(final String source) {
    final SyntaxNode root =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("m", AnalysisLanguage.TYPESCRIPT, source))
            .tree();
    return new NodeUselessConstructorCheck("useless").matches(root);
  }
}
