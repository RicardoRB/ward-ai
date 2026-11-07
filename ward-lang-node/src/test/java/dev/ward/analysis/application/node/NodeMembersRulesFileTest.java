package dev.ward.analysis.application.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The class-member and type-parameter naming rules are listed and name both languages. */
class NodeMembersRulesFileTest {

  private static final String FILE = "rules/node/naming.toml";

  @Test
  void ruleFilesListTheMembersFile() {
    assertThat(new NodeRuleFiles().ruleFiles()).contains(FILE);
    assertThat(NodeMembersRulesFileTest.class.getResource("/" + FILE)).isNotNull();
  }

  @Test
  void memberNameExistsForTypeScriptAndJavaScript() throws IOException {
    assertThat(ids()).contains("ward:typescript/member-name", "ward:javascript/member-name");
  }

  @Test
  void typeParameterRulesExistForTypeScriptOnly() throws IOException {
    final List<String> ids = ids();

    assertThat(ids)
        .contains(
            "ward:typescript/class-type-parameter-name",
            "ward:typescript/interface-type-parameter-name",
            "ward:typescript/method-type-parameter-name")
        .noneMatch(id -> id.startsWith("ward:javascript/") && id.contains("type-parameter"));
  }

  private static List<String> ids() throws IOException {
    try (InputStream in = NodeMembersRulesFileTest.class.getResourceAsStream("/" + FILE)) {
      assertThat(in).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .filter(line -> line.startsWith("id = \""))
          .map(line -> line.substring("id = \"".length(), line.length() - 1))
          .toList();
    }
  }
}
