package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ArchitectureConstraint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fory.json.ForyJson;
import org.junit.jupiter.api.Test;

/** The published schema of {@code ward-architecture.toml} names the keys the reader reads. */
class ArchitectureSchemaTest {
  private static final Path SCHEMA =
      Path.of("..", "website", "public", "schemas", "ward-architecture.schema.json");
  private static final String PROPERTIES = "properties";

  @Test
  void theTopLevelKeysAreTheReadersKeys() throws IOException {
    assertThat(child(schema(), PROPERTIES).keySet())
        .containsExactlyInAnyOrderElementsOf(ArchitectureFileReader.TOP_LEVEL);
  }

  @Test
  void theRuleKeysAreTheReadersKeysAndTheFiveConstraints() throws IOException {
    final Set<String> expected = new HashSet<>(ArchitectureFileReader.RULE_KEYS);
    for (final ArchitectureConstraint.Kind kind : ArchitectureConstraint.Kind.values()) {
      expected.add(kind.key());
    }

    assertThat(child(definition("rule"), PROPERTIES).keySet())
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void theSelectorKeysAreTheReadersKeys() throws IOException {
    assertThat(child(definition("selector"), PROPERTIES).keySet())
        .containsExactlyInAnyOrderElementsOf(ArchitectureFileReader.SELECTOR_KEYS);
  }

  @Test
  void everyConstraintIsOneOfTheChoicesOfARule() throws IOException {
    final List<?> choices = (List<?>) definition("rule").get("oneOf");

    final Set<String> required = new HashSet<>();
    for (final Object choice : choices) {
      ((List<?>) ((Map<?, ?>) choice).get("required"))
          .forEach(key -> required.add(String.valueOf(key)));
    }
    final Set<String> constraints = new HashSet<>();
    for (final ArchitectureConstraint.Kind kind : ArchitectureConstraint.Kind.values()) {
      constraints.add(kind.key());
    }
    assertThat(required).isEqualTo(constraints);
  }

  private static Map<String, Object> schema() throws IOException {
    return map(
        ForyJson.builder()
            .build()
            .fromJson(Files.readString(SCHEMA, StandardCharsets.UTF_8), Object.class));
  }

  private static Map<String, Object> definition(final String name) throws IOException {
    return child(child(schema(), "$defs"), name);
  }

  private static Map<String, Object> child(final Map<String, Object> parent, final String key) {
    return map(parent.get(key));
  }

  private static Map<String, Object> map(final Object value) {
    final Map<String, Object> copy = new LinkedHashMap<>();
    ((Map<?, ?>) value).forEach((key, entry) -> copy.put(String.valueOf(key), entry));
    return copy;
  }
}
