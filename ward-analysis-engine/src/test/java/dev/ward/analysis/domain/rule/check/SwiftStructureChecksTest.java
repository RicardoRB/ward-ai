package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class SwiftStructureChecksTest {

  private static final int VALUE_6 = 6;
  private static final int VALUE_20 = 20;
  private static final int VALUE_30 = 30;
  private static final int VALUE_40 = 40;
  private static final int VALUE_100 = 100;

  private static SyntaxNode member(final String text, final int start) {
    return SwiftTestNodes.node(
        "class_member", start, start + text.length(), SwiftTestNodes.leaf("token", text, start));
  }

  private static List<RuleMatch> grouping(final SyntaxNode... members) {
    return new ConstructorGroupCheck("{value} misplaced")
        .matches(
            SwiftTestNodes.node(
                "file", 0, VALUE_100, SwiftTestNodes.node("struct_body", 0, VALUE_100, members)));
  }

  @Test
  void constructorGroupReportsAnInitializerAfterAnotherMember() {
    final List<RuleMatch> found =
        grouping(
            member("init(x:Int){}", 1),
            member("func scale(){}", VALUE_20),
            member("init(){}", VALUE_40));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).span().startOffset()).isEqualTo(VALUE_40);
  }

  @Test
  void constructorGroupAcceptsGroupedInitializers() {
    assertThat(
            grouping(
                member("@objcinit(x:Int){}", 1),
                member("publicinit(){}", VALUE_20),
                member("var y=0", VALUE_40)))
        .isEmpty();
  }

  private static SyntaxNode importNode(final String name, final int start) {
    return SwiftTestNodes.node(
        "import_declaration",
        start,
        start + VALUE_6 + name.length(),
        SwiftTestNodes.leaf("token", "import" + name, start));
  }

  private static List<RuleMatch> imports(final SyntaxNode... imports) {
    return new ImportSortCheck("{value} unsorted")
        .matches(SwiftTestNodes.node("file", 0, VALUE_100, imports));
  }

  @Test
  void importSortReportsAnImportBeforeItsPredecessor() {
    final List<RuleMatch> found =
        imports(importNode("Foundation", 0), importNode("Combine", VALUE_30));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("Combine unsorted");
  }

  @Test
  void importSortIgnoresAttributesBeforeTheKeyword() {
    final SyntaxNode testable =
        SwiftTestNodes.node(
            "import_declaration",
            0,
            VALUE_20,
            SwiftTestNodes.leaf("token", "@testableimportShop", 0));

    assertThat(imports(importNode("Combine", VALUE_30), testable)).isEmpty();
  }

  @Test
  void importSortAcceptsSortedImports() {
    assertThat(imports(importNode("Combine", 0), importNode("Foundation", VALUE_30))).isEmpty();
  }
}
