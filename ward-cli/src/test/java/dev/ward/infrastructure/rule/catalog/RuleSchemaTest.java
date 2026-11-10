package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The JSON Schema of {@code ward-rules.toml} is published with the website and is committed, so an
 * editor always has it. This test fails when the committed file no longer matches the rule catalog.
 * Regenerate it with: {@code ./mvnw -Dtest=RuleSchemaTest -Dward.rules.schema.write=true test}.
 */
class RuleSchemaTest {

  private static final String WRITE = "ward.rules.schema.write";
  private static final Path COMMITTED =
      Path.of("..", "website", "public", "schemas", "ward-rules.schema.json");

  @Test
  void theCommittedSchemaMatchesTheRuleCatalog() throws IOException {
    final String generated = RuleSchema.render(CatalogRuleDefinitions.shipped());
    if (Boolean.getBoolean(WRITE)) {
      Files.createDirectories(COMMITTED.getParent());
      Files.writeString(COMMITTED, generated, StandardCharsets.UTF_8);
    }

    assertThat(COMMITTED)
        .as("committed schema is stale; regenerate it with -D%s=true", WRITE)
        .isRegularFile();
    assertThat(Files.readString(COMMITTED, StandardCharsets.UTF_8))
        .as("committed schema is stale; regenerate it with -D%s=true", WRITE)
        .isEqualTo(generated);
  }

  @Test
  void theSchemaListsEveryShippedRuleId() {
    final String generated = RuleSchema.render(CatalogRuleDefinitions.shipped());
    final List<String> missing =
        CatalogRuleDefinitions.shipped().stream()
            .map(definition -> definition.id())
            .filter(id -> !generated.contains("\"" + id + "\": {"))
            .toList();

    assertThat(missing).isEmpty();
  }
}
