package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Reads the JSON the harness needs: nested objects, arrays, escapes and literals. */
class MiniJsonTest {

  private static final double NEGATIVE = 25.0;

  @Test
  void readsNestedValuesAndEscapes() {
    final Map<?, ?> root =
        (Map<?, ?>)
            MiniJson.parse(
                " {\"a\": [1, -2.5e1, true, false, null], \"b\": {\"c\": \"x\\\"y\\u0041\\n\"}} ");

    assertThat(root.get("a")).isEqualTo(Arrays.asList(1.0, -NEGATIVE, true, false, null));
    assertThat(root.get("b")).isEqualTo(Map.of("c", "x\"yA\n"));
  }

  @Test
  void keepsTheMemberOrderOfAnObject() {
    final Map<String, Object> object = (Map<String, Object>) MiniJson.parse("{\"z\":1,\"a\":2}");

    assertThat(object.keySet()).containsExactly("z", "a");
  }

  @Test
  void rejectsTextAfterTheDocument() {
    assertThatThrownBy(() -> MiniJson.parse("{} x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("after the document");
  }

  @Test
  void rejectsAnUnterminatedString() {
    assertThatThrownBy(() -> MiniJson.parse("\"abc")).isInstanceOf(IllegalArgumentException.class);
  }
}
