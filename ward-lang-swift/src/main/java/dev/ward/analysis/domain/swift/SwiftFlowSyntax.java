package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The syntax reads that the Swift control-flow lowering, its accesses and its sites share. */
final class SwiftFlowSyntax {
  /** Declarations that are bodies of their own, so a statement does not enter them. */
  static final Set<String> OTHER_BODIES =
      Set.of(
          "function_declaration",
          "initializer_declaration",
          "deinitializer_declaration",
          "class_declaration",
          "struct_declaration",
          "extension_declaration",
          "protocol_declaration",
          "enum_declaration",
          "union_style_enum",
          "raw_value_style_enum"
      );

  private static final String STATEMENT = "statement";

  private SwiftFlowSyntax() {}

  /** The direct children of {@code node} with {@code kind}, in source order. */
  static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).toList();
  }

  /** The first direct child of {@code node} with {@code kind}, or null. */
  static SyntaxNode child(final SyntaxNode node, final String kind) {
    return children(node, kind).stream().findFirst().orElse(null);
  }

  /** The first node of {@code kind} at or below {@code node}, or null. */
  static SyntaxNode find(final SyntaxNode node, final String kind) {
    if (kind.equals(node.kind())) {
      return node;
    }
    for (final SyntaxNode child : node.children()) {
      final SyntaxNode found = find(child, kind);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  /** The statements of a code block or a case body, in order; nested ones are not listed. */
  static List<SyntaxNode> statements(final SyntaxNode block) {
    final List<SyntaxNode> found = new ArrayList<>();
    if (block != null) {
      collect(block, found);
    }
    return found;
  }

  private static void collect(final SyntaxNode node, final List<SyntaxNode> found) {
    for (final SyntaxNode child : node.children()) {
      if (STATEMENT.equals(child.kind())) {
        found.add(child);
      } else if (!STATEMENT.equals(node.kind())) {
        collect(child, found);
      }
    }
  }

  /** Adds a statement or condition node to the current block of a body. */
  static void step(final ControlFlowBuilder flow, final SyntaxNode node) {
    flow.add(node, mayThrow(node));
  }

  /** The text of a node without its spaces. */
  static String text(final SyntaxNode node) {
    return SwiftTrees.compact(node);
  }

  /** The span from the start of {@code from} to the end of {@code to}. */
  static Span between(final Span from, final Span to) {
    return new Span(
        from.startLine(),
        from.startColumn(),
        to.endLine(),
        to.endColumn(),
        from.startOffset(),
        to.endOffset()
    );
  }

  /** Whether {@code inner} lies within {@code outer}. */
  static boolean contains(final Span outer, final Span inner) {
    return outer.startOffset() <= inner.startOffset() && inner.endOffset() <= outer.endOffset();
  }

  /** Whether a node or anything under it is a call, or {@code try}, so it may throw. */
  static boolean mayThrow(final SyntaxNode node) {
    if (OTHER_BODIES.contains(node.kind())) {
      return false;
    }
    return "function_call_argument_clause".equals(node.kind())
        || "try_operator".equals(node.kind())
        || node.children().stream().anyMatch(SwiftFlowSyntax::mayThrow);
  }
}
