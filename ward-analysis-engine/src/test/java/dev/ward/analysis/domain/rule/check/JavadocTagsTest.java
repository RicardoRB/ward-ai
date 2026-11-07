package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JavadocTagsTest {

  @Test
  void readsTagsContinuationsAndPositionsOfAMultiLineComment() {
    final String text =
        "/**\n * Adds.\n *\n * @param left the first\n *     number\n * @return sum\n */";
    final SyntaxNode comment =
        new SyntaxNode(
            "comment",
            new Span(10, 3, 16, 4, 100, 100 + text.length()),
            Map.of("text", text),
            List.of());

    final List<JavadocTags.Tag> tags = JavadocTags.tags(comment);

    assertThat(tags).extracting(JavadocTags.Tag::name).containsExactly("@param", "@return");
    assertThat(tags.get(0).description()).isEqualTo("left the first");
    assertThat(tags.get(0).continuation()).hasSize(1);
    assertThat(tags.get(0).continuation().get(0).indent()).isEqualTo(5);
    assertThat(tags.get(0).span().startLine()).isEqualTo(13);
    assertThat(tags.get(0).span().startColumn()).isEqualTo(4);
    assertThat(tags.get(1).span().startLine()).isEqualTo(15);
    assertThat(tags.get(1).continuation()).isEmpty();
  }
}
