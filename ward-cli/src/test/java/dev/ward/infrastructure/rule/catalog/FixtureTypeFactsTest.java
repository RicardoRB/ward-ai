package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A fixture folder with a types.json gives its facts to type-aware rules. */
class FixtureTypeFactsTest {

  private static final Span CALL = new Span(5, 9, 5, 25, 70, 86);
  private static final SourceUnit UNIT =
      new SourceUnit("A.java", AnalysisLanguage.JAVA, "class A {}");

  @TempDir Path folder;

  @Test
  void aTypesJsonAnswersTheFactsOfItsSpans() throws IOException {
    final Path file =
        Files.writeString(
            folder.resolve("types.json"),
            "{\"5:9-5:25\": {\"type\": {\"name\": \"java.lang.String\"}}}");

    final TypeFacts facts = FixtureTypeFacts.of(Optional.of(file));

    assertThat(facts.factsOf(UNIT, List.of(CALL), Set.of(FactKind.TYPE)).typeOf(CALL))
        .contains(TypeRef.of("java.lang.String"));
  }

  @Test
  void withoutATypesJsonEveryFactIsUnknown() {
    final TypeFacts facts = FixtureTypeFacts.of(Optional.empty());

    assertThat(facts.factsOf(UNIT, List.of(CALL), Set.of(FactKind.TYPE)).typeOf(CALL)).isEmpty();
  }

  @Test
  void aMalformedTypesJsonFails() throws IOException {
    final Path file = Files.writeString(folder.resolve("types.json"), "[1]");

    assertThatThrownBy(() -> FixtureTypeFacts.of(Optional.of(file)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
