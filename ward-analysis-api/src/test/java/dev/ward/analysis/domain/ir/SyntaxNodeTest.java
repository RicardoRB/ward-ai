package dev.ward.analysis.domain.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SyntaxNodeTest {

  private static final Span SPAN = new Span(1, 1, 1, 5, 0, 4);

  @Test
  void keepsKindAndFields() {
    final SyntaxNode node =
        new SyntaxNode("method_declaration", SPAN, Map.of("visibility", "private"), List.of());

    assertThat(node.kind()).isEqualTo("method_declaration");
    assertThat(node.fields()).containsEntry("visibility", "private");
    assertThat(node.span()).isEqualTo(SPAN);
  }

  @Test
  void copiesChildrenOnConstruction() {
    final List<SyntaxNode> children = new ArrayList<>();
    final SyntaxNode node = new SyntaxNode("file", SPAN, Map.of(), children);
    children.add(new SyntaxNode("leaf", SPAN, Map.of(), List.of()));

    assertThat(node.children()).isEmpty();
  }

  @Test
  void returnsUnmodifiableChildren() {
    final SyntaxNode node = new SyntaxNode("file", SPAN, Map.of(), List.of());

    assertThatThrownBy(() -> node.children().add(node))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void copiesFieldsOnConstruction() {
    final Map<String, String> fields = new java.util.HashMap<>(Map.of("name", "a"));
    final SyntaxNode node = new SyntaxNode("file", SPAN, fields, List.of());
    fields.put("name", "b");

    assertThat(node.fields()).containsEntry("name", "a");
  }
}
