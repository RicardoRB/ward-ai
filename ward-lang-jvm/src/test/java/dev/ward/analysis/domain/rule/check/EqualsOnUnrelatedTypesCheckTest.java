package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.List;
import org.junit.jupiter.api.Test;

class EqualsOnUnrelatedTypesCheckTest {

  @Test
  void declaresTheNodeAndFactKindsItReads() {
    assertThat(Calls.CHECK.needs().nodeKinds()).containsExactly("expression");
    assertThat(Calls.CHECK.needs().facts())
        .containsExactlyInAnyOrder(FactKind.TYPE, FactKind.SHAPE);
  }

  @Test
  void withoutTypeFactsItReportsNothing() {
    assertThat(Calls.CHECK.matches(Calls.call(Calls.EQUALS, Calls.ARGUMENT))).isEmpty();
  }

  @Test
  void twoDifferentFinalTypesAreReportedWithBothNames() {
    final List<RuleMatch> found =
        Calls.run(
            Calls.call(Calls.EQUALS, Calls.ARGUMENT),
            Calls.table(
                Calls.RECEIVER,
                Calls.type(Calls.TYPE_A, true),
                Calls.ARGUMENT,
                Calls.type(Calls.TYPE_B, true)));

    assertThat(found)
        .singleElement()
        .satisfies(m -> assertThat(m.message()).isEqualTo("a.A and b.B"));
  }

  @Test
  void enumsAndRecordsCountAsFinal() {
    final SpanFacts anEnum = Calls.facts(TypeRef.of("a.E"), TypeShape.Kind.ENUM, false);
    final SpanFacts aRecord = Calls.facts(TypeRef.of("b.R"), TypeShape.Kind.RECORD, false);

    assertThat(
            Calls.run(
                Calls.call(Calls.EQUALS, Calls.ARGUMENT),
                Calls.table(Calls.RECEIVER, anEnum, Calls.ARGUMENT, aRecord)))
        .hasSize(1);
  }
}
