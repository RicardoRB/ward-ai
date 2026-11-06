package dev.ward.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.types.ConstantValue;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TypeSidecarJsonTest {
  private static final long CONSTANT = 7L;
  private static final Span AT = new Span(1, 1, 1, 5, 0, 4);

  @Test
  void encodesSpanOffsetsKindsAndEscapesTheText() {
    final String line =
        TypeSidecarJson.request(
            "src/A.java", "a\"b\\c\n\u0001", List.of(AT), Set.of(FactKind.TYPE, FactKind.CALL));

    assertThat(line)
        .isEqualTo(
            "{\"path\":\"src/A.java\",\"text\":\"a\\\"b\\\\c\\n\\u0001\",\"spans\":[[0,4]],"
                + "\"kinds\":[\"call\",\"type\"]}");
  }

  @Test
  void keysSpansByOffsets() {
    assertThat(TypeSidecarJson.key(AT)).isEqualTo("0:4");
  }

  @Test
  void readsEveryFactOfAResponse() {
    final Map<String, SpanFacts> facts =
        TypeSidecarJson.facts(
            "{\"facts\":{\"0:4\":{\"type\":{\"name\":\"java.util.List\",\"args\":[{\"name\":\"java.lang.String\"}],"
                + "\"array\":1},\"shape\":{\"kind\":\"interface\",\"abstract\":true,\"supertypes\":[]},"
                + "\"call\":{\"declaring\":{\"name\":\"java.util.List\"},\"name\":\"of\",\"params\":[],"
                + "\"static\":true},\"annotations\":[\"a.B\"],\"scopes\":[\"c.D\"],"
                + "\"overrides\":[],\"thrown\":[{\"name\":\"java.io.IOException\"}],\"constant\":"
                + CONSTANT
                + "}}}");

    final SpanFacts found = facts.get("0:4");
    assertThat(found.type().display()).isEqualTo("java.util.List[]");
    assertThat(found.type().arguments()).containsExactly(TypeRef.of("java.lang.String"));
    assertThat(found.shape().kind()).isEqualTo(TypeShape.Kind.INTERFACE);
    assertThat(found.call().isStatic()).isTrue();
    assertThat(found.call().returnType()).isNull();
    assertThat(found.annotations()).containsExactly("a.B");
    assertThat(found.scopes()).containsExactly("c.D");
    assertThat(found.thrown()).containsExactly(TypeRef.of("java.io.IOException"));
    assertThat(found.constant()).isEqualTo(new ConstantValue(CONSTANT));
  }

  @Test
  void readsAnEmptyAnswer() {
    assertThat(TypeSidecarJson.facts("{\"facts\":{}}")).isEmpty();
  }

  @Test
  void rejectsAResponseWithoutFacts() {
    assertThatThrownBy(() -> TypeSidecarJson.facts("{\"error\":\"boom\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> TypeSidecarJson.facts("{\"facts\":"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAFactOfTheWrongShape() {
    assertThatThrownBy(() -> TypeSidecarJson.facts("{\"facts\":{\"0:4\":{\"type\":7}}}"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
