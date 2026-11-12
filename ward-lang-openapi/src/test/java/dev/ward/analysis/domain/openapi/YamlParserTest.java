package dev.ward.analysis.domain.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

class YamlParserTest {

  @Test
  void readsBlockMappingsAndSequences() {
    assertThat(YamlTrees.dump("a: 1\nb:\n  c: x\n  d: [1, two]\ne:\n- p\n- q: r\n  s: t\n"))
        .isEqualTo("{a=1,b={c=\"x\",d=[1,\"two\"]},e=[\"p\",{q=\"r\",s=\"t\"}]}");
  }

  @Test
  void resolvesScalarTypesByTheCoreSchema() {
    assertThat(YamlTrees.dump("a: true\nb: null\nc: ~\nd: 1.5e3\ne: yes\nf: 0x1F\ng: \"1\"\nh:\n"))
        .isEqualTo("{a=true,b=~,c=~,d=1.5e3,e=\"yes\",f=0x1F,g=\"1\",h=~}");
  }

  @Test
  void numericKeysAreText() {
    final SyntaxNode key =
        YamlParser.parse("200: ok\n").tree().children().getFirst().children().getFirst();
    assertThat(key.kind()).isEqualTo(YamlSyntax.KEY);
    assertThat(key.fields()).containsEntry(YamlSyntax.VALUE, "200");
  }

  @Test
  void readsQuotedScalarsAndEscapes() {
    assertThat(YamlTrees.dump("a: 'it''s'\nb: \"tab\\there \\u00e9 \\\\\"\nc: \"x\n  y\"\n"))
        .isEqualTo("{a=\"it's\",b=\"tab\there \u00e9 \\\",c=\"x y\"}");
  }

  @Test
  void readsBlockScalars() {
    assertThat(
            YamlTrees.dump(
                "a: |\n  one\n  two\nb: >\n  fold\n  ed\n\n  para\nc: |-\n  x\n\nd: 1\n"))
        .isEqualTo("{a=\"one\ntwo\n\",b=\"fold ed\npara\n\",c=\"x\",d=1}");
  }

  @Test
  void keepAndStripChomping() {
    assertThat(YamlTrees.dump("a: |+\n  x\n\nb: |-\n  y\n")).isEqualTo("{a=\"x\n\n\",b=\"y\"}");
  }

  @Test
  void readsMultiLinePlainScalars() {
    assertThat(YamlTrees.dump("a: first\n  second\n\n  third\nb: 2\n"))
        .isEqualTo("{a=\"first second\nthird\",b=2}");
  }

  @Test
  void ignoresCommentsAndTrailingComments() {
    assertThat(YamlTrees.dump("# top\na: 1 # one\n# mid\nb: 'x' # two\nc: [1, # in\n 2]\n"))
        .isEqualTo("{a=1,b=\"x\",c=[1,2]}");
  }
}
