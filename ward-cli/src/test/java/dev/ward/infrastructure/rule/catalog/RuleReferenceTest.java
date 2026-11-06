package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.catalog.CatalogRow;
import dev.ward.analysis.domain.rule.catalog.RuleCatalog;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The rule reference of the website is generated from the catalog, and this test fails when a
 * committed page differs from the generated one. Regenerate the pages with: {@code ./mvnw
 * -Dtest=RuleReferenceTest -Dward.rules.reference.write=true test}.
 */
class RuleReferenceTest {

  private static final String WRITE = "ward.rules.reference.write";
  private static final Path DOCS = Path.of("..", "website", "src", "content", "docs");
  private static final Path GENERATED_DIRECTORY = DOCS.resolve("reference").resolve("rules");

  @Test
  void committedPagesEqualTheGeneratedPages() throws IOException {
    Assumptions.assumeFalse(Boolean.getBoolean(WRITE), "regenerating the pages");
    final Map<String, String> pages = generate(new RuleCatalogReader().load());
    final List<String> differing = new ArrayList<>();
    for (final Map.Entry<String, String> page : pages.entrySet()) {
      final Path file = DOCS.resolve(page.getKey());
      if (!Files.isRegularFile(file) || !Files.readString(file).equals(page.getValue())) {
        differing.add(page.getKey());
      }
    }

    assertThat(differing)
        .as(
            "rule reference pages that differ from the catalog; regenerate with"
                + " ./mvnw -Dtest=RuleReferenceTest -Dward.rules.reference.write=true test")
        .isEmpty();
    assertThat(committedFiles()).containsExactlyInAnyOrderElementsOf(generatedFiles(pages));
  }

  @Test
  void aChangedCatalogChangesTheGeneratedPages() throws IOException {
    final RuleCatalog catalog = new RuleCatalogReader().load();
    final List<CatalogRow> rows = new ArrayList<>(catalog.rows());
    rows.addAll(
        new RuleCatalogReader()
            .parse(
                "catalog/checkstyle-sizes.toml",
                new StringReader(
                    """
                    [[rule]]
                    name = "brand-new-rule"
                    category = "sizes"
                    status = { java = "planned" }
                    issues = { java = "#284" }
                    """)));

    final Map<String, String> changed = generate(new RuleCatalog(rows));

    assertThat(changed.get("reference/rules/checkstyle-sizes.mdx")).contains("`brand-new-rule`");
    assertThat(changed).isNotEqualTo(generate(catalog));
  }

  @Test
  void regenerateTheCommittedPagesWhenAsked() throws IOException {
    Assumptions.assumeTrue(Boolean.getBoolean(WRITE), "set -D" + WRITE + "=true to regenerate");
    final Map<String, String> pages = generate(new RuleCatalogReader().load());
    for (final Map.Entry<String, String> page : pages.entrySet()) {
      final Path file = DOCS.resolve(page.getKey());
      Files.createDirectories(file.getParent());
      Files.writeString(file, page.getValue(), StandardCharsets.UTF_8);
    }
    for (final String stale : committedFiles()) {
      if (!generatedFiles(pages).contains(stale)) {
        Files.delete(GENERATED_DIRECTORY.resolve(stale));
      }
    }
    assertThat(DOCS.resolve(RuleReference.INDEX)).isRegularFile();
  }

  private static Map<String, String> generate(final RuleCatalog catalog) {
    final List<RuleDefinition> definitions = new ClasspathRuleSource().load().rules();
    return RuleReference.pages(catalog, definitions, UpstreamLists.outOfScope());
  }

  private static List<String> generatedFiles(final Map<String, String> pages) {
    return pages.keySet().stream()
        .filter(key -> key.startsWith("reference/rules/"))
        .map(key -> key.substring("reference/rules/".length()))
        .toList();
  }

  private static List<String> committedFiles() throws IOException {
    if (!Files.isDirectory(GENERATED_DIRECTORY)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(GENERATED_DIRECTORY)) {
      return files.map(file -> file.getFileName().toString()).toList();
    }
  }
}
