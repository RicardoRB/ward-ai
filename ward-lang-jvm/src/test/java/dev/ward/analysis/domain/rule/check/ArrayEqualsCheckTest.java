package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArrayEqualsCheckTest {

  private static final String EQUALS = "equals";
  private static final TypeRef INTS = MoreTrees.type("int", 1, true);
  private static final TypeRef INTS_2D = MoreTrees.type("int", 2, true);
  private static final TypeRef LONGS = MoreTrees.type("long", 1, true);
  private static final TypeRef STRING = MoreTrees.type("java.lang.String", 0, false);
  private static final TypeRef STRINGS = MoreTrees.type("java.lang.String", 1, false);
  private static final TypeRef INTEGERS = MoreTrees.type("java.lang.Integer", 1, false);
  private static final TypeRef OBJECT = MoreTrees.type("java.lang.Object", 0, false);
  private static final ArrayEqualsCheck NON_ARRAY =
      new ArrayEqualsCheck(ArrayEqualsCheck.Mismatch.ARRAY_AND_NON_ARRAY, "message");
  private static final ArrayEqualsCheck ARRAYS =
      new ArrayEqualsCheck(ArrayEqualsCheck.Mismatch.INCOMPATIBLE_ARRAYS, "message");

  private static UnitTypeFacts types(final TypeRef left, final TypeRef right) {
    return Calls.table(
        Calls.RECEIVER,
        Calls.facts(left, TypeShape.Kind.CLASS, false),
        Calls.ARGUMENT,
        Calls.facts(right, TypeShape.Kind.CLASS, false));
  }

  private static List<RuleMatch> run(
      final ArrayEqualsCheck check, final String method, final UnitTypeFacts facts) {
    return check.matches(
        Calls.call(method, Calls.ARGUMENT), FlowView.NONE, ScopeView.NONE, facts, Calls.UNIT);
  }

  @Test
  void anArrayAndAValueThatIsNotAnArrayAreReportedEitherWayRound() {
    assertThat(NON_ARRAY.needs().facts()).containsExactly(FactKind.TYPE);
    assertThat(NON_ARRAY.matches(Calls.call(EQUALS, Calls.ARGUMENT))).isEmpty();
    assertThat(run(NON_ARRAY, EQUALS, types(INTS, STRING))).hasSize(1);
    assertThat(run(NON_ARRAY, EQUALS, types(STRING, INTS))).hasSize(1);
  }

  @Test
  void aTypeThatAnArrayCanHaveOrTwoArraysOrUnknownTypesAreNotReported() {
    assertThat(run(NON_ARRAY, EQUALS, types(INTS, OBJECT))).isEmpty();
    assertThat(run(NON_ARRAY, EQUALS, types(INTS, LONGS))).isEmpty();
    assertThat(run(NON_ARRAY, EQUALS, UnitTypeFacts.none())).isEmpty();
  }

  @Test
  void arraysOfDifferentPrimitiveElementTypesOrDepthsAreReported() {
    assertThat(run(ARRAYS, EQUALS, types(INTS, LONGS))).hasSize(1);
    assertThat(run(ARRAYS, EQUALS, types(INTS, INTS_2D))).hasSize(1);
    assertThat(run(ARRAYS, EQUALS, types(INTS, STRINGS))).hasSize(1);
  }

  @Test
  void sameArraysObjectArraysAndNonArraysAreNotReported() {
    assertThat(run(ARRAYS, EQUALS, types(INTS, INTS))).isEmpty();
    assertThat(run(ARRAYS, EQUALS, types(STRINGS, INTEGERS))).isEmpty();
    assertThat(run(ARRAYS, EQUALS, types(INTS, STRING))).isEmpty();
  }

  @Test
  void onlyACallOfEqualsWithOneArgumentIsChecked() {
    final UnitTypeFacts facts = types(INTS, STRING);

    assertThat(run(NON_ARRAY, "hashCode", facts)).isEmpty();
    assertThat(
            NON_ARRAY.matches(
                Calls.call(EQUALS, Calls.ARGUMENT, Calls.SECOND),
                FlowView.NONE,
                ScopeView.NONE,
                facts,
                Calls.UNIT))
        .isEmpty();
    assertThat(
            NON_ARRAY.matches(
                MoreTrees.node("expression", 1), FlowView.NONE, ScopeView.NONE, facts, Calls.UNIT))
        .isEmpty();
  }
}
