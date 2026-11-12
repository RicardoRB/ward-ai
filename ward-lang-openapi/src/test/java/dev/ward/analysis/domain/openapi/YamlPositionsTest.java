package dev.ward.analysis.domain.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

class YamlPositionsTest {

  private static final int LONG_LINE = 200_000;

  @Test
  void expandsAnchorsAliasesAndMerges() {
    assertThat(YamlTrees.dump("base: &b\n  x: 1\n  y: 2\nuse:\n  <<: *b\n  y: 3\nref: *b\n"))
        .isEqualTo("{base={x=1,y=2},use={x=1,y=3},ref={x=1,y=2}}");
  }

  @Test
  void anExpandedAliasKeepsItsSiteAndItsOriginalPosition() {
    final SyntaxNode root = YamlParser.parse("a: &x\n  k: 1\nb: *x\n").tree();
    final SyntaxNode alias = root.children().get(1).children().getLast();
    assertThat(alias.fields())
        .containsEntry(YamlSyntax.ALIAS, "x")
        .containsEntry(YamlSyntax.ORIGIN, "2:3");
    assertThat(YamlTrees.at(alias.span())).isEqualTo("3:4");
    assertThat(YamlTrees.at(alias.children().getFirst().children().getLast().span()))
        .isEqualTo("2:6");
  }

  @Test
  void jsonAndYamlGiveTheSameTree() {
    final String json = "{\"a\": [1, \"two\", true, null], \"b\": {\"c\": 1.5}, \"d\": \"\"}";
    final String yaml = "a:\n  - 1\n  - two\n  - true\n  - null\nb:\n  c: 1.5\nd: \"\"\n";
    assertThat(YamlTrees.dump(json)).isEqualTo(YamlTrees.dump(yaml));
    assertThat(YamlTrees.dump(json)).startsWith("{a=[1,");
  }

  @Test
  void aMinifiedJsonDocumentHasColumnsOnItsOneLine() {
    final SyntaxNode root = YamlParser.parse("{\"a\":1,\"bb\":[true]}").tree();
    final Span second = root.children().get(1).children().getFirst().span();
    assertThat(YamlTrees.at(second)).isEqualTo("1:8");
    assertThat(YamlTrees.end(second)).isEqualTo("1:12");
  }

  @Test
  void positionsAreLinesAndColumnsOfKeysAndValues() {
    final SyntaxNode root = YamlParser.parse("a:\n  b: 12\n").tree();
    final SyntaxNode inner = root.children().getFirst().children().getLast();
    final SyntaxNode value = inner.children().getFirst().children().getLast();
    assertThat(YamlTrees.at(value.span())).isEqualTo("2:6");
    assertThat(YamlTrees.end(value.span())).isEqualTo("2:8");
    assertThat(value.span().startOffset()).isEqualTo(YamlTrees.OFFSET_OF_TWELVE);
  }

  @Test
  void crlfLineEndingsAndAByteOrderMarkKeepPositions() {
    final SyntaxNode root = YamlParser.parse("\uFEFFa: 1\r\nb: 2\r\n").tree();
    final Span first = root.children().getFirst().children().getFirst().span();
    final Span second = root.children().getLast().children().getFirst().span();
    assertThat(YamlTrees.at(first)).isEqualTo("1:1");
    assertThat(first.startOffset()).isEqualTo(1);
    assertThat(YamlTrees.at(second)).isEqualTo("2:1");
  }

  @Test
  void aVeryLongLineIsRead() {
    final String value = "x".repeat(LONG_LINE);
    assertThat(YamlTrees.dump("a: " + value + "\n")).isEqualTo("{a=\"" + value + "\"}");
  }

  @Test
  void aNullValueHasAZeroWidthPosition() {
    final SyntaxNode value =
        YamlParser.parse("a:\nb: 1\n").tree().children().getFirst().children().getLast();
    assertThat(value.kind()).isEqualTo(YamlSyntax.NULL);
    assertThat(YamlTrees.at(value.span())).isEqualTo("1:3");
  }
}
