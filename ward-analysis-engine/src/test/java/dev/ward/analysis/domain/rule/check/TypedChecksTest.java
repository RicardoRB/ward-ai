package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.Layout.node;
import static dev.ward.analysis.domain.rule.check.TypedTrees.COMPILATION_UNIT;
import static dev.ward.analysis.domain.rule.check.TypedTrees.DEBUG;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the type-aware checks over trees whose nodes carry facts keyed by their start. */
class TypedChecksTest {

  private final TypedTrees trees = new TypedTrees();

  private static final String LOGGER = "org.slf4j.Logger";
  private static final String RESULT_SET = "java.sql.ResultSet";
  private static final String MESSAGE = "message {value}";
  private static final SourceUnit UNIT = new SourceUnit("x.java", AnalysisLanguage.JAVA, "");

  @Test
  void aConcatenatedDebugMessageOutsideAnIfIsReported() {
    final Layout layout = new Layout();
    final SyntaxNode call = trees.logCall(layout, DEBUG, true);
    final SyntaxNode root = node(COMPILATION_UNIT, call);
    assertThat(
            run(
                new GuardLogStatementCheck(MESSAGE),
                root,
                trees.at(call, trees.callOf(LOGGER, DEBUG))))
        .hasSize(1);
  }

  @Test
  void aDebugMessageInsideAnIfOrWithoutConcatenationIsNotReported() {
    final Layout guardedLayout = new Layout();
    final SyntaxNode ifToken = guardedLayout.token("'if'", "if");
    final SyntaxNode guardedCall = trees.logCall(guardedLayout, DEBUG, true);
    final SyntaxNode guarded = node(COMPILATION_UNIT, node("statement", ifToken, guardedCall));
    final Layout plainLayout = new Layout();
    final SyntaxNode plainCall = trees.logCall(plainLayout, DEBUG, false);
    final SyntaxNode plain = node(COMPILATION_UNIT, plainCall);
    assertThat(
            run(
                new GuardLogStatementCheck(MESSAGE),
                guarded,
                trees.at(guardedCall, trees.callOf(LOGGER, DEBUG))))
        .isEmpty();
    assertThat(
            run(
                new GuardLogStatementCheck(MESSAGE),
                plain,
                trees.at(plainCall, trees.callOf(LOGGER, DEBUG))))
        .isEmpty();
  }

  @Test
  void aNonDebugLevelOrANonLoggerCallIsNotReported() {
    final Layout infoLayout = new Layout();
    final SyntaxNode info = trees.logCall(infoLayout, "info", true);
    assertThat(
            run(
                new GuardLogStatementCheck(MESSAGE),
                node(COMPILATION_UNIT, info),
                trees.at(info, trees.callOf(LOGGER, "info"))))
        .isEmpty();
    final Layout listLayout = new Layout();
    final SyntaxNode list = trees.logCall(listLayout, DEBUG, true);
    assertThat(
            run(
                new GuardLogStatementCheck(MESSAGE),
                node(COMPILATION_UNIT, list),
                trees.at(list, trees.callOf("java.util.List", DEBUG))))
        .isEmpty();
  }

  @Test
  void anEqualsCallOnAnEnumReceiverOrArgumentIsReported() {
    final Layout layout = new Layout();
    final SyntaxNode call = trees.equalsCall(layout);
    final SyntaxNode root = node(COMPILATION_UNIT, call);
    final SyntaxNode argument = call.children().get(0);
    assertThat(
            run(
                new EnumComparisonCheck(MESSAGE),
                root,
                trees.at(
                    call,
                    trees.callOf("java.lang.Object", "equals"),
                    trees.shape(TypeShape.Kind.ENUM))))
        .hasSize(1);
    assertThat(
            run(
                new EnumComparisonCheck(MESSAGE),
                root,
                trees.at(
                    call,
                    trees.callOf("java.lang.Object", "equals"),
                    trees.shape(TypeShape.Kind.CLASS))))
        .isEmpty();
    assertThat(argument).isNotNull();
  }

  @Test
  void aCollectionKeyedByAnEnumIsReported() {
    final Layout enumLayout = new Layout();
    final SyntaxNode enumKey = trees.creator(enumLayout, "HashMap", "Color");
    assertThat(
            run(
                new UseEnumCollectionsCheck(MESSAGE),
                node(COMPILATION_UNIT, enumKey),
                trees.at(enumKey.children().get(2), trees.shape(TypeShape.Kind.ENUM))))
        .hasSize(1);
    final Layout stringLayout = new Layout();
    final SyntaxNode stringKey = trees.creator(stringLayout, "HashMap", "String");
    assertThat(
            run(
                new UseEnumCollectionsCheck(MESSAGE),
                node(COMPILATION_UNIT, stringKey),
                trees.at(stringKey.children().get(2), trees.shape(TypeShape.Kind.CLASS))))
        .isEmpty();
    final Layout listLayout = new Layout();
    final SyntaxNode list = trees.creator(listLayout, "ArrayList", "Color");
    assertThat(
            run(
                new UseEnumCollectionsCheck(MESSAGE),
                node(COMPILATION_UNIT, list),
                trees.at(list.children().get(2), trees.shape(TypeShape.Kind.ENUM))))
        .isEmpty();
  }

  @Test
  void aNextCallOnAResultSetAsAStatementIsReported() {
    final Layout layout = new Layout();
    final SyntaxNode next = trees.nextExpression(layout);
    final SyntaxNode statement = node("statement", next, layout.token("';'", ";"));
    final SyntaxNode root = node(COMPILATION_UNIT, statement);
    assertThat(
            run(
                new CheckResultSetCheck(MESSAGE),
                root,
                trees.at(next, trees.callOf(RESULT_SET, "next"))))
        .hasSize(1);
    assertThat(
            run(
                new CheckResultSetCheck(MESSAGE),
                root,
                trees.at(next, trees.callOf("java.util.Iterator", "next"))))
        .isEmpty();
  }

  @Test
  void aReturnedCallIsNotAStatementAndIsNotReported() {
    final Layout layout = new Layout();
    final SyntaxNode returned =
        node("statement", layout.token("'return'", "return"), trees.nextExpression(layout));
    final SyntaxNode root = node(COMPILATION_UNIT, returned);
    assertThat(
            run(
                new CheckResultSetCheck(MESSAGE),
                root,
                trees.at(returned.children().get(1), trees.callOf(RESULT_SET, "next"))))
        .isEmpty();
  }

  @Test
  void theTypedHelpersReadTheirFactsAndTheChecksDeclareTheirNeeds() {
    final Layout layout = new Layout();
    final SyntaxNode call = trees.logCall(layout, DEBUG, true);
    final UnitTypeFacts facts =
        trees.at(call, trees.callOf(LOGGER, DEBUG), trees.shape(TypeShape.Kind.CLASS));
    assertThat(TypedChecks.calledName(call, facts)).contains(DEBUG);
    assertThat(TypedChecks.declaringType(call, facts)).contains(LOGGER);
    assertThat(TypedChecks.isEnum(call, facts)).isFalse();
    assertThat(new CheckResultSetCheck(MESSAGE).typeNeeds()).containsExactly(TypedChecks.CALLS);
    assertThat(new EnumComparisonCheck(MESSAGE).typeNeeds())
        .containsExactlyInAnyOrder(TypedChecks.CALLS, TypedChecks.SHAPES);
    assertThat(new CheckResultSetCheck(MESSAGE).matches(call)).isEmpty();
  }

  private static List<RuleMatch> run(
      final RuleCheck check, final SyntaxNode root, final UnitTypeFacts facts) {
    return check.matches(root, null, ScopeView.NONE, UNIT, facts);
  }
}
