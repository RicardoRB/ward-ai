package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

class VarargPrimitiveArrayCheckTest {

  private static final String FORMAT = "format";
  private static final TypeRef OBJECTS = MoreTrees.type("java.lang.Object", 1, false);
  private static final TypeRef STRING = MoreTrees.type("java.lang.String", 0, false);
  private static final SpanFacts INTS = MoreTrees.typed(MoreTrees.type("int", 1, true));
  private static final VarargPrimitiveArrayCheck CHECK = new VarargPrimitiveArrayCheck("message");

  private static List<RuleMatch> run(final SyntaxNode root, final UnitTypeFacts facts) {
    return CHECK.matches(root, FlowView.NONE, ScopeView.NONE, facts, Calls.UNIT);
  }

  private static UnitTypeFacts facts(
      final List<TypeRef> parameters, final boolean varargs, final SpanFacts argument) {
    return MoreTrees.table(
        Calls.CALL, MoreTrees.called(parameters, varargs), Calls.SECOND, argument);
  }

  private static SyntaxNode call() {
    return Calls.call(FORMAT, Calls.ARGUMENT, Calls.SECOND);
  }

  @Test
  void aPrimitiveArrayInTheVariablePartIsReported() {
    assertThat(CHECK.needs().facts()).containsExactlyInAnyOrder(FactKind.CALL, FactKind.TYPE);
    assertThat(CHECK.matches(call())).isEmpty();
    assertThat(run(call(), facts(List.of(STRING, OBJECTS), true, INTS))).hasSize(1);
  }

  @Test
  void anArrayBeforeTheVariablePartIsNotReported() {
    final UnitTypeFacts facts =
        MoreTrees.table(
            Calls.CALL, MoreTrees.called(List.of(STRING, OBJECTS), true), Calls.ARGUMENT, INTS);

    assertThat(run(call(), facts)).isEmpty();
  }

  @Test
  void aCallThatDoesNotSpreadObjectsIsNotReported() {
    assertThat(run(call(), facts(List.of(STRING, OBJECTS), false, INTS))).isEmpty();
    assertThat(run(call(), facts(List.of(), true, INTS))).isEmpty();
    assertThat(run(call(), facts(List.of(STRING, STRING), true, INTS))).isEmpty();
    assertThat(
            run(
                call(),
                facts(List.of(STRING, MoreTrees.type("java.lang.Object", 0, false)), true, INTS)))
        .isEmpty();
    assertThat(run(call(), facts(List.of(STRING, OBJECTS), true, MoreTrees.typed(STRING))))
        .isEmpty();
    assertThat(run(call(), UnitTypeFacts.none())).isEmpty();
  }

  @Test
  void aCallWithoutArgumentsIsNotReported() {
    final UnitTypeFacts facts =
        MoreTrees.table(Calls.CALL, MoreTrees.called(List.of(OBJECTS), true));

    assertThat(run(MoreTrees.node("expression", 1), facts)).isEmpty();
    assertThat(run(Calls.call(FORMAT), facts)).isEmpty();
  }
}
