package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

class EqualsOnUnrelatedTypesKnownFactsTest {

  @Test
  void theSameTypeIsNotReported() {
    assertThat(
            Calls.run(
                Calls.call(Calls.EQUALS, Calls.ARGUMENT),
                Calls.table(
                    Calls.RECEIVER,
                    Calls.type(Calls.TYPE_A, true),
                    Calls.ARGUMENT,
                    Calls.type(Calls.TYPE_A, true))))
        .isEmpty();
  }

  @Test
  void aNonFinalTypeOnEitherSideIsNotReported() {
    assertThat(
            Calls.run(
                Calls.call(Calls.EQUALS, Calls.ARGUMENT),
                Calls.table(
                    Calls.RECEIVER,
                    Calls.type(Calls.TYPE_A, false),
                    Calls.ARGUMENT,
                    Calls.type(Calls.TYPE_B, true))))
        .isEmpty();
    assertThat(
            Calls.run(
                Calls.call(Calls.EQUALS, Calls.ARGUMENT),
                Calls.table(
                    Calls.RECEIVER,
                    Calls.type(Calls.TYPE_A, true),
                    Calls.ARGUMENT,
                    Calls.type(Calls.TYPE_B, false))))
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
            Calls.run(
                Calls.call(Calls.EQUALS, Calls.ARGUMENT),
                Calls.table(
                    Calls.RECEIVER, primitive, Calls.ARGUMENT, Calls.type(Calls.TYPE_B, true))))
        .isEmpty();
  }

  @Test
  void aTypeWithoutShapeOrWithoutTypeIsUnknown() {
    final SpanFacts noShape =
        new SpanFacts(
            TypeRef.of(Calls.TYPE_A), null, null, List.of(), List.of(), List.of(), List.of(), null);

    assertThat(
            Calls.run(
                Calls.call(Calls.EQUALS, Calls.ARGUMENT),
                Calls.table(
                    Calls.RECEIVER, noShape, Calls.ARGUMENT, Calls.type(Calls.TYPE_B, true))))
        .isEmpty();
    assertThat(
            Calls.run(
                Calls.call(Calls.EQUALS, Calls.ARGUMENT),
                Calls.table(Calls.RECEIVER, Calls.type(Calls.TYPE_A, true))))
        .isEmpty();
  }

  @Test
  void otherMethodsAndOtherArgumentCountsAreIgnored() {
    final UnitTypeFacts known =
        Calls.table(
            Calls.RECEIVER,
            Calls.type(Calls.TYPE_A, true),
            Calls.ARGUMENT,
            Calls.type(Calls.TYPE_B, true),
            Calls.SECOND,
            Calls.type("c.C", true));

    assertThat(Calls.run(Calls.call("compareTo", Calls.ARGUMENT), known)).isEmpty();
    assertThat(Calls.run(Calls.call(Calls.EQUALS), known)).isEmpty();
    assertThat(Calls.run(Calls.call(Calls.EQUALS, Calls.ARGUMENT, Calls.SECOND), known)).isEmpty();
  }

  @Test
  void aNodeThatIsNotAMemberCallIsIgnored() {
    final SyntaxNode plain =
        Calls.node("expression", Calls.CALL, Calls.node("primary", Calls.RECEIVER));
    final SyntaxNode noReceiver =
        Calls.node("expression", Calls.CALL, Calls.node("methodCall", Calls.CALL));

    assertThat(Calls.run(plain, Calls.table(Calls.RECEIVER, Calls.type(Calls.TYPE_A, true))))
        .isEmpty();
    assertThat(Calls.run(noReceiver, Calls.table(Calls.RECEIVER, Calls.type(Calls.TYPE_A, true))))
        .isEmpty();
  }
}
