package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArrayReceiverCallCheckTest {

  private static final String HASH_CODE = "hashCode";
  private static final SpanFacts ARRAY =
      Calls.facts(new TypeRef("int", List.of(), 1, true), TypeShape.Kind.CLASS, false);
  private static final SpanFacts STRING = Calls.type("java.lang.String", true);

  private static List<RuleMatch> run(
      final String method, final int arguments, final SyntaxNode root, final UnitTypeFacts types) {
    return new ArrayReceiverCallCheck(method, arguments, "message")
        .matches(root, FlowView.NONE, ScopeView.NONE, types, Calls.UNIT);
  }

  @Test
  void declaresTheNodeAndFactKindsItReads() {
    final ArrayReceiverCallCheck check = new ArrayReceiverCallCheck(HASH_CODE, 0, "message");

    assertThat(check.needs().nodeKinds()).containsExactly("expression");
    assertThat(check.needs().facts()).containsExactly(FactKind.TYPE);
    assertThat(check.matches(Calls.call(HASH_CODE))).isEmpty();
  }

  @Test
  void aCallOfTheNamedMethodOnAnArrayIsReported() {
    final List<RuleMatch> found =
        run(HASH_CODE, 0, Calls.call(HASH_CODE), Calls.table(Calls.RECEIVER, ARRAY));

    assertThat(found).singleElement().satisfies(m -> assertThat(m.message()).isEqualTo("message"));
  }

  @Test
  void aReceiverThatIsNotAnArrayIsNotReported() {
    assertThat(run(HASH_CODE, 0, Calls.call(HASH_CODE), Calls.table(Calls.RECEIVER, STRING)))
        .isEmpty();
  }

  @Test
  void anUnknownReceiverTypeIsNotReported() {
    assertThat(run(HASH_CODE, 0, Calls.call(HASH_CODE), UnitTypeFacts.none())).isEmpty();
  }

  @Test
  void anotherMethodNameIsNotReported() {
    assertThat(run("toString", 0, Calls.call(HASH_CODE), Calls.table(Calls.RECEIVER, ARRAY)))
        .isEmpty();
  }

  @Test
  void anotherArgumentCountIsNotReported() {
    assertThat(
            run(
                HASH_CODE,
                0,
                Calls.call(HASH_CODE, Calls.ARGUMENT),
                Calls.table(Calls.RECEIVER, ARRAY)))
        .isEmpty();
    assertThat(
            run(
                "equals",
                1,
                Calls.call("equals", Calls.ARGUMENT),
                Calls.table(Calls.RECEIVER, ARRAY)))
        .hasSize(1);
  }
}
