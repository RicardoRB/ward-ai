package dev.ward.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypeSidecarJsonTest {
  private static final Span AT = new Span(1, 1, 1, 5, 0, 4);

  @Test
  void encodesSpanOffsetsAndEscapesTheText() {
    final String line = TypeSidecarJson.request("src/A.java", "a\"b\\c\n\u0001", List.of(AT));

    assertThat(line)
        .isEqualTo(
            "{\"path\":\"src/A.java\",\"text\":\"a\\\"b\\\\c\\n\\u0001\",\"spans\":[[0,4]]}");
  }

  @Test
  void keysSpansByOffsets() {
    assertThat(TypeSidecarJson.key(AT)).isEqualTo("0:4");
  }

  @Test
  void readsTypeNamesFromTheResponse() {
    assertThat(
            TypeSidecarJson.types("{\"types\":{\"0:4\":\"java.lang.String\",\"5:9\":\"number\"}}"))
        .containsExactlyInAnyOrderEntriesOf(Map.of("0:4", "java.lang.String", "5:9", "number"));
  }

  @Test
  void readsAnEmptyAnswer() {
    assertThat(TypeSidecarJson.types("{\"types\":{}}")).isEmpty();
  }

  @Test
  void rejectsAResponseWithoutTypes() {
    assertThatThrownBy(() -> TypeSidecarJson.types("{\"error\":\"boom\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> TypeSidecarJson.types("{\"types\":"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonStringTypeNames() {
    assertThatThrownBy(() -> TypeSidecarJson.types("{\"types\":{\"0:4\":7}}"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
