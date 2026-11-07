package dev.ward.analysis.domain.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.port.TypeFacts;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CachedTypeFactsTest {
  private static final Span AT = new Span(1, 1, 1, 2, 0, 1);
  private static final int DISTINCT_REQUESTS = 3;
  private static final List<Span> SPANS = List.of(AT);

  private final AtomicInteger requests = new AtomicInteger();
  private final TypeFacts resolver =
      (unit, spans, kinds) -> {
        requests.incrementAndGet();
        return UnitTypeFacts.none();
      };

  @Test
  void anUnchangedUnitIsAskedOnce() {
    final TypeFacts cached = new CachedTypeFacts(resolver, "classpath-1");
    final SourceUnit unit = new SourceUnit("A.java", AnalysisLanguage.JAVA, "class A {}");

    cached.factsOf(unit, SPANS, EnumSet.of(FactKind.TYPE));
    cached.factsOf(unit, SPANS, EnumSet.of(FactKind.TYPE));

    assertThat(requests).hasValue(1);
  }

  @Test
  void changedContentOrOtherKindsAreAskedAgain() {
    final TypeFacts cached = new CachedTypeFacts(resolver, "classpath-1");

    cached.factsOf(
        new SourceUnit("A.java", AnalysisLanguage.JAVA, "class A {}"),
        SPANS,
        EnumSet.of(FactKind.TYPE));
    cached.factsOf(
        new SourceUnit("A.java", AnalysisLanguage.JAVA, "class B {}"),
        SPANS,
        EnumSet.of(FactKind.TYPE));
    cached.factsOf(
        new SourceUnit("A.java", AnalysisLanguage.JAVA, "class B {}"),
        SPANS,
        EnumSet.of(FactKind.CALL));

    assertThat(requests).hasValue(DISTINCT_REQUESTS);
  }

  @Test
  void aDifferentClasspathHashDoesNotShareAnswers() {
    final SourceUnit unit = new SourceUnit("A.java", AnalysisLanguage.JAVA, "class A {}");

    new CachedTypeFacts(resolver, "one").factsOf(unit, SPANS, EnumSet.of(FactKind.TYPE));
    new CachedTypeFacts(resolver, "two").factsOf(unit, SPANS, EnumSet.of(FactKind.TYPE));

    assertThat(requests).hasValue(2);
  }

  @Test
  void theTypeNameViewReadsTheTypeFacts() {
    final TypeFacts facts =
        (unit, spans, kinds) ->
            new UnitTypeFacts(
                java.util.Map.of(
                    UnitTypeFacts.key(AT),
                    new SpanFacts(
                        new TypeRef("java.lang.String", List.of(), 1, false),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null)));

    assertThat(facts.typesOf(new SourceUnit("A.java", AnalysisLanguage.JAVA, "x"), SPANS).types())
        .containsEntry(AT, "java.lang.String[]");
  }
}
