package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.Layout.node;
import static dev.ward.analysis.domain.rule.check.Layout.startOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Runs the enum switch checks over switches on an enum declared in the same unit. */
class EnumSwitchChecksTest {

  private static final SourceUnit UNIT = new SourceUnit("x.java", AnalysisLanguage.JAVA, "");
  private static final String MESSAGE = "message";
  private static final String IDENTIFIER = "identifier";
  private static final String TOKEN_IDENTIFIER = "IDENTIFIER";
  private static final String EXPRESSION = "expression";
  private static final String LABEL = "switchLabel";
  private static final String GROUP = "switchBlockStatementGroup";

  @Test
  void aDefaultOnAFullyCoveredEnumSwitchIsReported() {
    final Scene scene = scene(List.of("RED", "BLUE"), true, false);
    assertThat(run(new ExhaustiveSwitchHasDefaultCheck(MESSAGE), scene)).hasSize(1);
  }

  @Test
  void aFullyCoveredSwitchWithoutDefaultIsNotReportedByTheDefaultCheck() {
    final Scene scene = scene(List.of("RED", "BLUE"), false, false);
    assertThat(run(new ExhaustiveSwitchHasDefaultCheck(MESSAGE), scene)).isEmpty();
  }

  @Test
  void aSwitchMissingAConstantWithoutDefaultIsReported() {
    final Scene scene = scene(List.of("RED"), false, false);
    assertThat(run(new NonExhaustiveSwitchCheck(MESSAGE), scene)).hasSize(1);
    assertThat(run(new ExhaustiveSwitchHasDefaultCheck(MESSAGE), scene)).isEmpty();
  }

  @Test
  void aSwitchWithDefaultOrCoveredConstantsIsNotReportedAsNonExhaustive() {
    assertThat(run(new NonExhaustiveSwitchCheck(MESSAGE), scene(List.of("RED"), true, false)))
        .isEmpty();
    assertThat(
            run(new NonExhaustiveSwitchCheck(MESSAGE), scene(List.of("RED", "BLUE"), false, false)))
        .isEmpty();
  }

  @Test
  void aSwitchOnAnEnumDeclaredElsewhereIsNotRead() {
    final Scene scene = scene(List.of("RED"), false, true);
    assertThat(run(new NonExhaustiveSwitchCheck(MESSAGE), scene)).isEmpty();
  }

  private static List<RuleMatch> run(final RuleCheck check, final Scene scene) {
    return check.matches(scene.root(), null, ScopeView.NONE, scene.facts(), UNIT);
  }

  /**
   * A unit with {@code enum Color { RED, BLUE }} and a switch on a {@code color} selector. The
   * selector's type is {@code Other} when {@code elsewhere}, which declares no constants here.
   */
  private static Scene scene(
      final List<String> cases, final boolean withDefault, final boolean elsewhere) {
    final Layout layout = new Layout();
    final SyntaxNode enumDeclaration = enumDeclaration(layout);
    final SyntaxNode selector = node(EXPRESSION, layout.token(TOKEN_IDENTIFIER, "color"));
    final List<SyntaxNode> parts =
        new ArrayList<>(
            List.of(
                layout.token("'switch'", "switch"),
                layout.token("'('", "("),
                selector,
                layout.token("')'", ")"),
                layout.token("'{'", "{")));
    for (final String constant : cases) {
      parts.add(
          node(
              GROUP,
              node(
                  LABEL,
                  layout.token("'case'", "case"),
                  node(EXPRESSION, layout.token(TOKEN_IDENTIFIER, constant)),
                  layout.token("':'", ":"))));
    }
    if (withDefault) {
      parts.add(node(LABEL, layout.token("'default'", "default"), layout.token("':'", ":")));
    }
    parts.add(layout.token("'}'", "}"));
    final SyntaxNode statement = node("statement", parts.toArray(SyntaxNode[]::new));
    final SyntaxNode root = node("compilationUnit", enumDeclaration, statement);
    final String type = elsewhere ? "Other" : "Color";
    final UnitTypeFacts facts = new UnitTypeFacts(Map.of(startOf(selector), fact(type)));
    return new Scene(root, facts);
  }

  /** {@code enum Color { RED, BLUE }}. */
  private static SyntaxNode enumDeclaration(final Layout layout) {
    final SyntaxNode keyword = layout.token("'enum'", "enum");
    final SyntaxNode name = node(IDENTIFIER, layout.token(TOKEN_IDENTIFIER, "Color"));
    final SyntaxNode open = layout.token("'{'", "{");
    final SyntaxNode red =
        node("enumConstant", node(IDENTIFIER, layout.token(TOKEN_IDENTIFIER, "RED")));
    final SyntaxNode comma = layout.token("','", ",");
    final SyntaxNode blue =
        node("enumConstant", node(IDENTIFIER, layout.token(TOKEN_IDENTIFIER, "BLUE")));
    final SyntaxNode close = layout.token("'}'", "}");
    return node(
        "enumDeclaration", keyword, name, open, node("enumConstants", red, comma, blue), close);
  }

  private static SpanFacts fact(final String typeName) {
    return new SpanFacts(
        TypeRef.of(typeName),
        new TypeShape(TypeShape.Kind.ENUM, false, false, false, List.of()),
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  /** A unit, its facts, and the root that a check runs over. */
  private record Scene(SyntaxNode root, UnitTypeFacts facts) {}
}
