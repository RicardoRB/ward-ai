package dev.ward.analysis.application.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The syntax style rules of the suggestion file exist for the languages they apply to. */
class NodeSyntaxStyleRulesFileTest {

  private static final String FILE = "rules/node/suggestion.toml";
  private static final List<String> BOTH =
      List.of(
          "prefer-exponentiation-operator",
          "prefer-numeric-literals",
          "no-unneeded-ternary",
          "use-nodejs-import-protocol");
  private static final List<String> TYPESCRIPT_ONLY =
      List.of(
          "no-namespace",
          "prefer-namespace-keyword",
          "no-empty-object-type",
          "no-require-imports",
          "triple-slash-reference");

  @Test
  void ruleFilesListTheSuggestionFile() {
    assertThat(new NodeRuleFiles().ruleFiles()).contains(FILE);
  }

  @Test
  void sharedRulesExistForBothLanguages() throws IOException {
    final List<String> ids = ids();

    for (final String name : BOTH) {
      assertThat(ids).contains("ward:typescript/" + name, "ward:javascript/" + name);
    }
  }

  @Test
  void typeScriptSyntaxRulesHaveNoJavaScriptTwin() throws IOException {
    final List<String> ids = ids();

    for (final String name : TYPESCRIPT_ONLY) {
      assertThat(ids).contains("ward:typescript/" + name).doesNotContain("ward:javascript/" + name);
    }
  }

  private static List<String> ids() throws IOException {
    try (InputStream in = NodeSyntaxStyleRulesFileTest.class.getResourceAsStream("/" + FILE)) {
      assertThat(in).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .filter(line -> line.startsWith("id = \""))
          .map(line -> line.substring("id = \"".length(), line.length() - 1))
          .toList();
    }
  }
}
