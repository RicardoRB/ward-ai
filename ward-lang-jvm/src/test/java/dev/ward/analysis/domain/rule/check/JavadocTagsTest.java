package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JavadocTagsTest {

  private static final int FIRST_LINE = 10;
  private static final int START_COLUMN = 3;
  private static final int END_LINE = 16;
  private static final int END_COLUMN = 4;
  private static final int START_OFFSET = 100;
  private static final int CONTINUATION_INDENT = 5;
  private static final int PARAM_LINE = 13;
  private static final int PARAM_COLUMN = 4;
  private static final int RETURN_LINE = 15;

  @Test
  void readsTagsContinuationsAndPositionsOfAMultiLineComment() {
    final String text =
        "/**\n * Adds.\n *\n * @param left the first\n *     number\n * @return sum\n */";
    final SyntaxNode comment =
        new SyntaxNode(
            "comment",
            new Span(
                FIRST_LINE,
                START_COLUMN,
                END_LINE,
                END_COLUMN,
                START_OFFSET,
                START_OFFSET + text.length()),
            Map.of("text", text),
            List.of());

    final List<JavadocTags.Tag> tags = JavadocTags.tags(comment);

    assertThat(tags).extracting(JavadocTags.Tag::name).containsExactly("@param", "@return");
    assertThat(tags.get(0).description()).isEqualTo("left the first");
    assertThat(tags.get(0).continuation()).hasSize(1);
    assertThat(tags.get(0).continuation().get(0).indent()).isEqualTo(CONTINUATION_INDENT);
    assertThat(tags.get(0).span().startLine()).isEqualTo(PARAM_LINE);
    assertThat(tags.get(0).span().startColumn()).isEqualTo(PARAM_COLUMN);
    assertThat(tags.get(1).span().startLine()).isEqualTo(RETURN_LINE);
    assertThat(tags.get(1).continuation()).isEmpty();
  }
}
