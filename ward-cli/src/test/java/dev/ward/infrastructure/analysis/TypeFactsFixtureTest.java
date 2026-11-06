package dev.ward.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.types.Nullness;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypeFactsFixtureTest {
  private static final Span PARAMETER = new Span(3, 12, 3, 20, 40, 48);
  private static final Span CALL = new Span(5, 9, 5, 25, 70, 86);

  @TempDir Path folder;

  @Test
  void buildsFactsFromATypesJsonNextToAFixture() throws IOException {
    final Path file =
        Files.writeString(
            folder.resolve("types.json"),
            """
            {
              "3:12": {"scopes": ["org.jspecify.annotations.NullMarked"]},
              "5:9-5:25": {
                "type": {"name": "java.lang.String"},
                "call": {"declaring": {"name": "java.lang.String"}, "name": "equals",
                         "params": [{"name": "java.lang.Object"}], "static": false},
                "shape": {"kind": "class", "final": true, "supertypes": [{"name": "java.lang.Object"}]},
                "thrown": [{"name": "java.io.IOException"}],
                "constant": "text"
              }
            }
            """);

    final UnitTypeFacts facts = TypeFactsFixture.read(file);

    assertThat(facts.nullness(PARAMETER)).isEqualTo(Nullness.NON_NULL);
    assertThat(facts.typeOf(CALL)).contains(TypeRef.of("java.lang.String"));
    assertThat(facts.callTarget(CALL).orElseThrow().name()).isEqualTo("equals");
    assertThat(facts.shapeOf(CALL).orElseThrow().kind()).isEqualTo(TypeShape.Kind.CLASS);
    assertThat(facts.at(CALL).thrown()).containsExactly(TypeRef.of("java.io.IOException"));
    assertThat(facts.constant(CALL).orElseThrow().value()).isEqualTo("text");
  }

  @Test
  void aSpanWithoutAFactIsUnknown() {
    assertThat(TypeFactsFixture.parse("{}").typeOf(CALL)).isEmpty();
  }

  @Test
  void rejectsMalformedFacts() {
    assertThatThrownBy(() -> TypeFactsFixture.parse("[1]"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TypeFactsFixture.parse("{\"1:1\": {\"type\": \"int\"}}"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TypeFactsFixture.read(folder.resolve("missing.json")))
        .isInstanceOf(java.io.UncheckedIOException.class);
  }
}
