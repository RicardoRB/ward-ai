package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.EqualsCallFixtures.ARGUMENT;
import static dev.ward.analysis.domain.rule.check.EqualsCallFixtures.CALL;
import static dev.ward.analysis.domain.rule.check.EqualsCallFixtures.EQUALS;
import static dev.ward.analysis.domain.rule.check.EqualsCallFixtures.RECEIVER;
import static dev.ward.analysis.domain.rule.check.EqualsCallFixtures.SECOND;
import static dev.ward.analysis.domain.rule.check.EqualsCallFixtures.TYPE_A;
import static dev.ward.analysis.domain.rule.check.EqualsCallFixtures.TYPE_B;
import static dev.ward.analysis.domain.rule.check.EqualsCallFixtures.call;
import static dev.ward.analysis.domain.rule.check.EqualsCallFixtures.facts;
import static dev.ward.analysis.domain.rule.check.EqualsCallFixtures.node;
import static dev.ward.analysis.domain.rule.check.EqualsCallFixtures.table;
import static dev.ward.analysis.domain.rule.check.EqualsCallFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
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

class EqualsOnUnrelatedTypesCheckTest {
  private static final SourceUnit UNIT = new SourceUnit("A.java", AnalysisLanguage.JAVA, "text");
  private static final EqualsOnUnrelatedTypesCheck CHECK =
      new EqualsOnUnrelatedTypesCheck("{value}");

  @Test
  void declaresTheNodeAndFactKindsItReads() {
    assertThat(CHECK.needs().nodeKinds()).containsExactly("expression");
    assertThat(CHECK.needs().facts()).containsExactlyInAnyOrder(FactKind.TYPE, FactKind.SHAPE);
  }

  @Test
  void withoutTypeFactsItReportsNothing() {
    assertThat(CHECK.matches(call(EQUALS, ARGUMENT))).isEmpty();
  }

  @Test
  void twoDifferentFinalTypesAreReportedWithBothNames() {
    final List<RuleMatch> found =
        run(
            call(EQUALS, ARGUMENT),
            table(RECEIVER, type(TYPE_A, true), ARGUMENT, type(TYPE_B, true)));

    assertThat(found)
        .singleElement()
        .satisfies(m -> assertThat(m.message()).isEqualTo("a.A and b.B"));
  }

  @Test
  void enumsAndRecordsCountAsFinal() {
    final SpanFacts anEnum = facts(TypeRef.of("a.E"), TypeShape.Kind.ENUM, false);
    final SpanFacts aRecord = facts(TypeRef.of("b.R"), TypeShape.Kind.RECORD, false);

    assertThat(run(call(EQUALS, ARGUMENT), table(RECEIVER, anEnum, ARGUMENT, aRecord))).hasSize(1);
  }

  @Test
  void theSameTypeIsNotReported() {
    assertThat(
            run(
                call(EQUALS, ARGUMENT),
                table(RECEIVER, type(TYPE_A, true), ARGUMENT, type(TYPE_A, true))))
        .isEmpty();
  }

  @Test
  void aNonFinalTypeOnEitherSideIsNotReported() {
    assertThat(
            run(
                call(EQUALS, ARGUMENT),
                table(RECEIVER, type(TYPE_A, false), ARGUMENT, type(TYPE_B, true))))
        .isEmpty();
    assertThat(
            run(
                call(EQUALS, ARGUMENT),
                table(RECEIVER, type(TYPE_A, true), ARGUMENT, type(TYPE_B, false))))
        .isEmpty();
  }

  @Test
  void aPrimitiveIsNotReported() {
    final SpanFacts primitive =
        new SpanFacts(
            new TypeRef("int", List.of(), 0, true),
            new TypeShape(TypeShape.Kind.CLASS, false, true, false, List.of()),
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null);

    assertThat(
            run(call(EQUALS, ARGUMENT), table(RECEIVER, primitive, ARGUMENT, type(TYPE_B, true))))
        .isEmpty();
  }

  @Test
  void aTypeWithoutShapeOrWithoutTypeIsUnknown() {
    final SpanFacts noShape =
        new SpanFacts(
            TypeRef.of(TYPE_A), null, null, List.of(), List.of(), List.of(), List.of(), null);

    assertThat(run(call(EQUALS, ARGUMENT), table(RECEIVER, noShape, ARGUMENT, type(TYPE_B, true))))
        .isEmpty();
    assertThat(run(call(EQUALS, ARGUMENT), table(RECEIVER, type(TYPE_A, true)))).isEmpty();
  }

  @Test
  void otherMethodsAndOtherArgumentCountsAreIgnored() {
    final UnitTypeFacts known =
        table(
            RECEIVER, type(TYPE_A, true), ARGUMENT, type(TYPE_B, true), SECOND, type("c.C", true));

    assertThat(run(call("compareTo", ARGUMENT), known)).isEmpty();
    assertThat(run(call(EQUALS), known)).isEmpty();
    assertThat(run(call(EQUALS, ARGUMENT, SECOND), known)).isEmpty();
  }

  @Test
  void aNodeThatIsNotAMemberCallIsIgnored() {
    final SyntaxNode plain = node("expression", CALL, node("primary", RECEIVER));
    final SyntaxNode noReceiver = node("expression", CALL, node("methodCall", CALL));

    assertThat(run(plain, table(RECEIVER, type(TYPE_A, true)))).isEmpty();
    assertThat(run(noReceiver, table(RECEIVER, type(TYPE_A, true)))).isEmpty();
  }

  private static List<RuleMatch> run(final SyntaxNode root, final UnitTypeFacts types) {
    return CHECK.matches(root, FlowView.NONE, ScopeView.NONE, types, UNIT);
  }
}
