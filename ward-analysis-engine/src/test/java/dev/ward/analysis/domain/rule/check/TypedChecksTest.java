package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Runs the type-aware checks over trees whose nodes carry facts keyed by their start position. */
class TypedChecksTest {

  private static final String LOGGER = "org.slf4j.Logger";
  private static final String RESULT_SET = "java.sql.ResultSet";
  private static final String EXPRESSION = "expression";
  private static final String IDENTIFIER = "identifier";
  private static final String TOKEN_IDENTIFIER = "IDENTIFIER";
  private static final String MESSAGE = "message {value}";
  private static final SourceUnit UNIT = new SourceUnit("x.java", AnalysisLanguage.JAVA, "");

  @Test
  void aConcatenatedDebugMessageOutsideAnIfIsReported() {
    final SyntaxNode root = root(logCall(1, 5, "debug", true));
    assertThat(run(new GuardLogStatementCheck(MESSAGE), root, at(1, 5, callOf(LOGGER, "debug"))))
        .hasSize(1);
  }

  @Test
  void aDebugMessageInsideAnIfOrWithoutConcatenationIsNotReported() {
    final SyntaxNode guarded =
        root(node("statement", 1, 1, leaf("'if'", "if", 1, 1), logCall(1, 9, "debug", true)));
    final SyntaxNode plain = root(logCall(2, 5, "debug", false));
    assertThat(run(new GuardLogStatementCheck(MESSAGE), guarded, at(1, 9, callOf(LOGGER, "debug"))))
        .isEmpty();
    assertThat(run(new GuardLogStatementCheck(MESSAGE), plain, at(2, 5, callOf(LOGGER, "debug"))))
        .isEmpty();
  }

  @Test
  void aNonDebugLevelOrANonLoggerCallIsNotReported() {
    final SyntaxNode info = root(logCall(1, 5, "info", true));
    assertThat(run(new GuardLogStatementCheck(MESSAGE), info, at(1, 5, callOf(LOGGER, "info"))))
        .isEmpty();
    final SyntaxNode list = root(logCall(2, 5, "debug", true));
    assertThat(
            run(
                new GuardLogStatementCheck(MESSAGE),
                list,
                at(2, 5, callOf("java.util.List", "debug"))))
        .isEmpty();
  }

  @Test
  void anEqualsCallOnAnEnumReceiverOrArgumentIsReported() {
    final SyntaxNode call = root(equalsCall(1, 5));
    assertThat(
            run(
                new EnumComparisonCheck(MESSAGE),
                call,
                at(1, 5, callOf("java.lang.Object", "equals"), shape(TypeShape.Kind.ENUM))))
        .hasSize(1);
    assertThat(
            run(
                new EnumComparisonCheck(MESSAGE),
                call,
                at(1, 5, callOf("java.lang.Object", "equals"), shape(TypeShape.Kind.CLASS))))
        .isEmpty();
    final UnitTypeFacts argumentEnum =
        new UnitTypeFacts(
            Map.of(
                "1:5",
                fact(callOf("java.lang.Object", "equals"), shape(TypeShape.Kind.CLASS)),
                "1:18",
                fact(shape(TypeShape.Kind.ENUM))));
    assertThat(run(new EnumComparisonCheck(MESSAGE), call, argumentEnum)).hasSize(1);
  }

  @Test
  void aCollectionKeyedByAnEnumIsReported() {
    final SyntaxNode enumKey = root(creator(1, 5, "HashMap", "Color"));
    assertThat(
            run(
                new UseEnumCollectionsCheck(MESSAGE),
                enumKey,
                at(1, 17, shape(TypeShape.Kind.ENUM))))
        .hasSize(1);
    final SyntaxNode stringKey = root(creator(2, 5, "HashMap", "String"));
    assertThat(
            run(
                new UseEnumCollectionsCheck(MESSAGE),
                stringKey,
                at(2, 17, shape(TypeShape.Kind.CLASS))))
        .isEmpty();
    final SyntaxNode other = root(creator(3, 5, "ArrayList", "Color"));
    assertThat(
            run(new UseEnumCollectionsCheck(MESSAGE), other, at(3, 17, shape(TypeShape.Kind.ENUM))))
        .isEmpty();
  }

  @Test
  void aNextCallOnAResultSetAsAStatementIsReported() {
    final SyntaxNode statement = root(nextStatement(1, 5));
    assertThat(
            run(new CheckResultSetCheck(MESSAGE), statement, at(1, 5, callOf(RESULT_SET, "next"))))
        .hasSize(1);
    assertThat(
            run(
                new CheckResultSetCheck(MESSAGE),
                statement,
                at(1, 5, callOf("java.util.Iterator", "next"))))
        .isEmpty();
  }

  @Test
  void aReturnedCallIsNotAStatementAndIsNotReported() {
    final SyntaxNode returned =
        root(node("statement", 1, 1, leaf("'return'", "return", 1, 1), nextExpression(1, 8)));
    assertThat(
            run(new CheckResultSetCheck(MESSAGE), returned, at(1, 8, callOf(RESULT_SET, "next"))))
        .isEmpty();
  }

  @Test
  void theTypedHelpersReadTheirFactsAndTheChecksDeclareTheirNeeds() {
    final UnitTypeFacts facts = at(1, 5, callOf(LOGGER, "debug"), shape(TypeShape.Kind.CLASS));
    final SyntaxNode call = logCall(1, 5, "debug", true);
    assertThat(TypedChecks.calledName(call, facts)).contains("debug");
    assertThat(TypedChecks.declaringType(call, facts)).contains(LOGGER);
    assertThat(TypedChecks.isEnum(call, facts)).isFalse();
    assertThat(new CheckResultSetCheck(MESSAGE).typeNeeds()).containsExactly(TypedChecks.CALLS);
    assertThat(new EnumComparisonCheck(MESSAGE).typeNeeds())
        .containsExactlyInAnyOrder(TypedChecks.CALLS, TypedChecks.SHAPES);
    assertThat(new CheckResultSetCheck(MESSAGE).matches(call)).isEmpty();
  }

  private static List<dev.ward.analysis.domain.rule.RuleMatch> run(
      final RuleCheck check, final SyntaxNode root, final UnitTypeFacts facts) {
    return check.matches(root, null, ScopeView.NONE, UNIT, facts);
  }

  /** Builds facts keyed by the start position of one node; each spec is a position and its fact. */
  private static UnitTypeFacts at(final int line, final int col, final Object... parts) {
    return new UnitTypeFacts(Map.of(line + ":" + col, fact(parts)));
  }

  private static SpanFacts fact(final Object... parts) {
    SpanFacts facts = SpanFacts.UNKNOWN;
    for (final Object part : parts) {
      if (part instanceof CallTarget call) {
        facts =
            new SpanFacts(
                null, facts.shape(), call, List.of(), List.of(), List.of(), List.of(), null);
      } else if (part instanceof TypeShape shape) {
        facts =
            new SpanFacts(
                null, shape, facts.call(), List.of(), List.of(), List.of(), List.of(), null);
      }
    }
    return facts;
  }

  private static CallTarget callOf(final String declaring, final String name) {
    return new CallTarget(TypeRef.of(declaring), name, List.of(), TypeRef.of("void"), false, false);
  }

  private static TypeShape shape(final TypeShape.Kind kind) {
    return new TypeShape(kind, false, false, false, List.of());
  }

  private static SyntaxNode root(final SyntaxNode child) {
    return node("compilationUnit", 1, 1, child);
  }

  private static SyntaxNode node(
      final String kind, final int line, final int col, final SyntaxNode... children) {
    return new SyntaxNode(
        kind, new Span(line, col, line, col + 1, 0, 1), Map.of(), List.of(children));
  }

  private static SyntaxNode leaf(
      final String kind, final String text, final int line, final int col) {
    return new SyntaxNode(
        kind,
        new Span(line, col, line, col + text.length(), 0, text.length()),
        Map.of("text", text),
        List.of());
  }

  /** {@code log.<name>("x" [+ name])} starting at the given position. */
  private static SyntaxNode logCall(
      final int line, final int col, final String name, final boolean concatenate) {
    final List<SyntaxNode> parts = new ArrayList<>();
    parts.add(leaf("'('", "(", line, col + 8));
    parts.add(node(EXPRESSION, line, col + 9, leaf(TOKEN_IDENTIFIER, "\"x\"", line, col + 9)));
    if (concatenate) {
      parts.add(leaf("'+'", "+", line, col + 12));
      parts.add(node(EXPRESSION, line, col + 14, leaf(TOKEN_IDENTIFIER, "name", line, col + 14)));
    }
    parts.add(leaf("')'", ")", line, col + 18));
    return node(
        EXPRESSION,
        line,
        col,
        node(EXPRESSION, line, col, leaf(TOKEN_IDENTIFIER, "log", line, col)),
        leaf("'.'", ".", line, col + 3),
        node(
            "methodCall",
            line,
            col + 4,
            leaf(TOKEN_IDENTIFIER, name, line, col + 4),
            node("arguments", line, col + 8, parts.toArray(SyntaxNode[]::new))));
  }

  /** {@code color.equals(RED)}: the argument {@code RED} starts 13 columns in. */
  private static SyntaxNode equalsCall(final int line, final int col) {
    return node(
        EXPRESSION,
        line,
        col,
        node(EXPRESSION, line, col, leaf(TOKEN_IDENTIFIER, "color", line, col)),
        leaf("'.'", ".", line, col + 5),
        node(
            "methodCall",
            line,
            col + 6,
            leaf(TOKEN_IDENTIFIER, "equals", line, col + 6),
            node(
                "arguments",
                line,
                col + 12,
                leaf("'('", "(", line, col + 12),
                node(EXPRESSION, line, col + 13, leaf(TOKEN_IDENTIFIER, "RED", line, col + 13)),
                leaf("')'", ")", line, col + 16))));
  }

  /** {@code new <name><<key>>()}: the key type starts 12 columns in. */
  private static SyntaxNode creator(
      final int line, final int col, final String name, final String key) {
    return node(
        "creator",
        line,
        col,
        node(IDENTIFIER, line, col + 4, leaf(TOKEN_IDENTIFIER, name, line, col + 4)),
        leaf("'<'", "<", line, col + 11),
        node("typeType", line, col + 12, leaf(TOKEN_IDENTIFIER, key, line, col + 12)),
        leaf("'>'", ">", line, col + 12 + key.length()));
  }

  /** {@code rows.next();} as an expression statement. */
  private static SyntaxNode nextStatement(final int line, final int col) {
    return node(
        "statement", line, col, nextExpression(line, col), leaf("';'", ";", line, col + 12));
  }

  private static SyntaxNode nextExpression(final int line, final int col) {
    return node(
        EXPRESSION,
        line,
        col,
        node(EXPRESSION, line, col, leaf(TOKEN_IDENTIFIER, "rows", line, col)),
        leaf("'.'", ".", line, col + 4),
        node("methodCall", line, col + 5, leaf(TOKEN_IDENTIFIER, "next", line, col + 5)));
  }
}
