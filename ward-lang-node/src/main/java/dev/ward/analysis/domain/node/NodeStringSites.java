package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Records the string-building flow sites of the lowering: a string literal or template that holds
 * value parts, from the shapes that {@link NodeStringBuilds} finds. Walks are supplied by the
 * caller.
 */
final class NodeStringSites {

  private NodeStringSites() {}

  /** A chain of {@code +} with a string literal operand is one string-building site. */
  static void concatenation(
      final SyntaxNode node,
      final List<FlowSite> found,
      final BiConsumer<SyntaxNode, List<FlowSite>> walk,
      final BiConsumer<SyntaxNode, List<FlowSite>> children) {
    final List<SyntaxNode> operands = NodeStringBuilds.operands(node);
    if (hasLiteral(operands)) {
      stringBuild(node.span(), operands, found, walk);
    } else {
      children.accept(node, found);
    }
  }

  /** A template with a substitution is one string-building site; a plain template is a literal. */
  static void template(
      final SyntaxNode node,
      final SyntaxNode template,
      final List<FlowSite> found,
      final BiConsumer<SyntaxNode, List<FlowSite>> walk,
      final BiConsumer<SyntaxNode, List<FlowSite>> children) {
    final List<SyntaxNode> values = NodeStringBuilds.substitutions(template);
    if (values.isEmpty()) {
      children.accept(node, found);
      return;
    }
    values.forEach(value -> walk.accept(value, found));
    found.add(
        new FlowSite(
            FlowSiteKind.STRING_BUILD,
            NodeStringBuilds.templateText(template),
            "",
            Optional.empty(),
            List.of(),
            NodeCallShapes.spans(values),
            node.span()));
  }

  /**
   * A string built from the operands: the literal operands join the name, and the other operands
   * are the arguments, whose sites are listed before.
   */
  static void stringBuild(
      final Span span,
      final List<SyntaxNode> operands,
      final List<FlowSite> found,
      final BiConsumer<SyntaxNode, List<FlowSite>> walk) {
    final StringBuilder text = new StringBuilder();
    final List<SyntaxNode> values = new ArrayList<>();
    for (final SyntaxNode operand : operands) {
      final Optional<String> literal = NodeStringBuilds.literalText(operand);
      if (literal.isPresent()) {
        text.append(literal.get());
      } else {
        values.add(operand);
      }
    }
    values.forEach(value -> walk.accept(value, found));
    found.add(
        new FlowSite(
            FlowSiteKind.STRING_BUILD,
            text.toString(),
            "",
            Optional.empty(),
            List.of(),
            NodeCallShapes.spans(values),
            span));
  }

  static boolean hasLiteral(final List<SyntaxNode> operands) {
    return operands.stream().anyMatch(operand -> NodeStringBuilds.literalText(operand).isPresent());
  }
}
