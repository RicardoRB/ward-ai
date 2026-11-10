package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TypeFactsPrePassTest {
  private static final Span CALL = new Span(1, 1, 1, 5, 0, 4);
  private static final Span FIELD = new Span(2, 1, 2, 5, 5, 9);
  private static final SourceUnit UNIT = new SourceUnit("A.java", AnalysisLanguage.JAVA, "text");
  private static final SyntaxNode ROOT =
      new SyntaxNode(
          "file",
          new Span(1, 1, 2, 5, 0, 9),
          Map.of(),
          List.of(
              new SyntaxNode("call", CALL, Map.of(), List.of()),
              new SyntaxNode("field", FIELD, Map.of(), List.of())));

  @Test
  void threeRulesForTheSameFileSendOneRequest() {
    final List<Request> requests = new ArrayList<>();
    final TypeFactsPrePass prePass = new TypeFactsPrePass(recording(requests));

    prePass.run(
        UNIT,
        ROOT,
        List.of(
            new FactNeed(Set.of("call"), Set.of(FactKind.CALL)),
            new FactNeed(Set.of("call", "field"), Set.of(FactKind.TYPE)),
            new FactNeed(Set.of("field"), Set.of(FactKind.ANNOTATIONS))));

    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).spans()).containsExactlyInAnyOrder(CALL, FIELD);
    assertThat(requests.get(0).kinds())
        .containsExactlyInAnyOrder(FactKind.CALL, FactKind.TYPE, FactKind.ANNOTATIONS);
  }

  @Test
  void onlyTheNodeKindsRulesDeclareAreSent() {
    final List<Request> requests = new ArrayList<>();

    new TypeFactsPrePass(recording(requests))
        .run(UNIT, ROOT, List.of(new FactNeed(Set.of("field"), Set.of(FactKind.TYPE))));

    assertThat(requests.get(0).spans()).containsExactly(FIELD);
  }

  @Test
  void noNeedOrNoMatchingNodeAsksNothing() {
    final List<Request> requests = new ArrayList<>();
    final TypeFactsPrePass prePass = new TypeFactsPrePass(recording(requests));

    assertThat(prePass.run(UNIT, ROOT, List.of())).isEqualTo(UnitTypeFacts.none());
    assertThat(
            prePass.run(UNIT, ROOT, List.of(new FactNeed(Set.of("absent"), Set.of(FactKind.TYPE)))))
        .isEqualTo(UnitTypeFacts.none());
    assertThat(requests).isEmpty();
  }

  private record Request(List<Span> spans, Set<FactKind> kinds) {}

  private static TypeFacts recording(final List<Request> requests) {
    return (unit, spans, kinds) -> {
      requests.add(new Request(spans, kinds));
      return UnitTypeFacts.none();
    };
  }
}
