package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NodeChecksTest {

  private static final String STATIC = "static";
  private static final String UNIT = "unit";
  private static final String FIELD = "field";
  private static final String NAME = "name";
  private static final String MODIFIER = "modifier";
  private static final String MEMBER = "member";
  private static final ModifierTable TABLE =
      new ModifierTable(Set.of(MODIFIER), Map.of(STATIC, Set.of(STATIC, "class")));

  private final TreeLayout gen = new TreeLayout();

  private NodeFilter fields() {
    return new NodeFilter(
        Set.of(FIELD), TABLE, Set.of(STATIC, "final"), Set.of(), Set.of(), Set.of(), Sources.ALL);
  }

  private SyntaxNode member(final String name, final String... modifiers) {
    final SyntaxNode[] parts = new SyntaxNode[modifiers.length + 1];
    for (int index = 0; index < modifiers.length; index++) {
      parts[index] = gen.node(MODIFIER, gen.leaf("tok", modifiers[index]));
    }
    parts[modifiers.length] = gen.node(FIELD, gen.leaf(NAME, name));
    return gen.node(MEMBER, parts);
  }

  private IdentifierPatternCheck constants(final NodeFilter filter) {
    return new IdentifierPatternCheck(
        filter, NAME, Set.of(), "^[A-Z][A-Z0-9_]*$", "bad constant {value}");
  }

  @Test
  void identifierPatternReportsTheNameOfAMatchingDeclaration() {
    final SyntaxNode root =
        gen.node(UNIT, member("maxSize", STATIC, "final"), member("MAX", STATIC, "final"));

    assertThat(TreeLayout.messages(constants(fields()).matches(root)))
        .containsExactly("bad constant maxSize");
  }

  @Test
  void identifierPatternSkipsDeclarationsThatLackAModifier() {
    final SyntaxNode root = gen.node(UNIT, member("maxSize", STATIC), member("other"));

    assertThat(constants(fields()).matches(root)).isEmpty();
  }

  @Test
  void modifierNamesAreReadThroughTheLanguageTable() {
    final NodeFilter staticOnly =
        new NodeFilter(
            Set.of(FIELD), TABLE, Set.of(STATIC), Set.of("final"), Set.of(), Set.of(), Sources.ALL);
    final SyntaxNode root = gen.node(UNIT, member("a1", "class"), member("b1", STATIC, "final"));

    assertThat(TreeLayout.messages(constants(staticOnly).matches(root)))
        .containsExactly("bad constant a1");
  }

  @Test
  void insideAndNotInsideLimitByAncestor() {
    final SyntaxNode also = gen.node(FIELD, gen.leaf(NAME, "also"));
    final SyntaxNode inner = gen.node("iface", gen.node(FIELD, gen.leaf(NAME, "low")));
    final SyntaxNode outer = gen.node("class", also, inner);
    final SyntaxNode root = gen.node(UNIT, outer);
    final NodeFilter notInInterface =
        new NodeFilter(
            Set.of(FIELD),
            TABLE,
            Set.of(),
            Set.of(),
            Set.of("class"),
            Set.of("iface"),
            Sources.ALL);

    assertThat(TreeLayout.messages(constants(notInInterface).matches(root)))
        .containsExactly("bad constant also");
  }

  @Test
  void sourcesLimitsTheCheckToTestOrMainFiles() {
    final SyntaxNode root = gen.node(UNIT, gen.node(FIELD, gen.leaf(NAME, "low")));
    final SourceUnit test = new SourceUnit("src/test/java/FooTest.java", AnalysisLanguage.JAVA, "");
    final SourceUnit main = new SourceUnit("src/main/java/Foo.java", AnalysisLanguage.JAVA, "");
    final IdentifierPatternCheck onlyTests =
        constants(
            new NodeFilter(
                Set.of(FIELD), TABLE, Set.of(), Set.of(), Set.of(), Set.of(), Sources.TEST));

    assertThat(onlyTests.matches(root, FlowView.NONE, test)).hasSize(1);
    assertThat(onlyTests.matches(root, FlowView.NONE, main)).isEmpty();
    assertThat(Sources.parse("main").admits(main.path())).isTrue();
    assertThat(Sources.MAIN.admits(test.path())).isFalse();
    assertThatThrownBy(() -> Sources.parse("some")).isInstanceOf(IllegalArgumentException.class);
  }
}
