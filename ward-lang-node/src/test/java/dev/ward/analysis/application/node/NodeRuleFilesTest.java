package dev.ward.analysis.application.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The rule files this module lists exist on the classpath and declare rules of both languages. */
class NodeRuleFilesTest {

  private static final String BIOME_FILE = "rules/node/biome.toml";

  @Test
  void everyListedFileIsOnTheClasspath() throws IOException {
    for (final String file : new NodeRuleFiles().ruleFiles()) {
      assertThat(read(file)).as(file).contains("[[rules]]");
    }
  }

  @Test
  void biomeFileIsListedAndDeclaresTypeScriptAndJavaScriptRules() throws IOException {
    assertThat(new NodeRuleFiles().ruleFiles()).contains(BIOME_FILE);

    final String text = read(BIOME_FILE);

    assertThat(text).contains("id = \"ward:typescript/no-for-each\"");
    assertThat(text).contains("id = \"ward:javascript/no-for-each\"");
    assertThat(text).doesNotContain("id = \"ward:javascript/no-const-enum\"");
  }

  @Test
  void listedFilesAreInAlphabeticalOrder() {
    assertThat(new NodeRuleFiles().ruleFiles()).isSorted();
  }

  private static String read(final String path) throws IOException {
    try (InputStream stream = NodeRuleFilesTest.class.getResourceAsStream("/" + path)) {
      assertThat(stream).as(path).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
