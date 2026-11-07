package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SwiftUnusedImportCheckTest {

  private static final int SECOND = 30;
  private static final int END = 100;

  private static SyntaxNode importNode(final String name, final int start) {
    return SwiftTestNodes.node(
        "import_declaration",
        start,
        start + 1,
        SwiftTestNodes.leaf("token", "import" + name, start));
  }

  private static List<RuleMatch> run(final SyntaxNode... children) {
    return new UnusedImportCheck("{value} unused")
        .matches(SwiftTestNodes.node("file", 0, END, children));
  }

  @Test
  void reportsAnImportWhoseModuleNameAppearsNowhereElse() {
    final List<RuleMatch> found =
        run(importNode("Foundation", 0), SwiftTestNodes.leaf("token", "count", SECOND));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("Foundation unused");
  }

  @Test
  void acceptsAModuleNamedInAQualifiedTypeOrByAnotherImportedTypeName() {
    assertThat(
            run(
                importNode("Foundation.NSString", 0),
                SwiftTestNodes.leaf("token", "Foundation", SECOND)))
        .isEmpty();
  }

  @Test
  void ignoresNamesThatOnlyAppearInComments() {
    final SyntaxNode comment =
        new SyntaxNode(
            "comment",
            SwiftTestNodes.span(SECOND, SECOND + 1),
            Map.of("text", "// Foundation"),
            List.of());

    assertThat(run(importNode("Foundation", 0), comment)).hasSize(1);
  }
}
