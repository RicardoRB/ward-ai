package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Finds the reads, writes and declarations of the names inside one Python statement or expression.
 * A name that the body declares {@code global} or {@code nonlocal} is not recorded, and neither are
 * the names of nested scopes, which have scopes of their own.
 */
final class PythonNameAccesses {

  private static final String NAME = "name";
  private static final String TRAILER = "trailer";
  private static final String ARGUMENT = "argument";
  private static final String NAMED_EXPR = "namedexpr_test";
  private static final String EQUALS = "=";
  private static final String WALRUS = ":=";
  private static final String DOT = ".";

  /** Nodes that bind or use names of another scope, which this body does not read or write. */
  private static final Set<String> OTHER_SCOPE =
      Set.of(
          "funcdef", "classdef", "decorated", "lambdef", "lambdef_nocond", "comp_for", "comp_if");

  private static final String STRING = "STRING";
  private static final Set<String> NOTHING = Set.of("global_stmt", "nonlocal_stmt", "pass_stmt");

  private final Set<String> excluded;

  /**
   * Creates the finder of one body.
   *
   * @param excluded the names the body declares {@code global} or {@code nonlocal}
   */
  PythonNameAccesses(final Set<String> excluded) {
    this.excluded = Set.copyOf(excluded);
  }

  /** Records a read of each name under {@code node}, in evaluation order. */
  void reads(final SyntaxNode node, final List<VariableAccess> out) {
    if (NAME.equals(node.kind())) {
      add(node, AccessKind.READ, out);
    } else if (STRING.equals(node.kind())) {
      PythonFStrings.reads(node, excluded, out);
    } else if (NAMED_EXPR.equals(node.kind())) {
      walrus(node, out);
    } else if (TRAILER.equals(node.kind())) {
      trailer(node, out);
    } else if (ARGUMENT.equals(node.kind())) {
      keywordArgument(node, out);
    } else if (!OTHER_SCOPE.contains(node.kind()) && !NOTHING.contains(node.kind())) {
      for (final SyntaxNode child : node.children()) {
        reads(child, out);
      }
    }
  }

  /** Records the names under {@code node} as writes, or as reads when it holds a subscript. */
  void targets(final SyntaxNode node, final List<VariableAccess> out) {
    if (hasTrailer(node)) {
      reads(node, out);
    } else {
      writeNames(node, out);
    }
  }

  /** Records the names under {@code node} as declarations, which unbind them. */
  void declare(final SyntaxNode node, final List<VariableAccess> out) {
    if (hasTrailer(node)) {
      reads(node, out);
      return;
    }
    final List<SyntaxNode> names = new ArrayList<>();
    collectNames(node, names);
    for (final SyntaxNode name : names) {
      add(name, AccessKind.DECLARE, out);
    }
  }

  /** Records every name under {@code node} as a write. */
  void writeNames(final SyntaxNode node, final List<VariableAccess> out) {
    if (NAME.equals(node.kind())) {
      add(node, AccessKind.WRITE, out);
      return;
    }
    if (OTHER_SCOPE.contains(node.kind())) {
      return;
    }
    for (final SyntaxNode child : node.children()) {
      writeNames(child, out);
    }
  }

  /** Records one name node as an access of {@code kind}, unless the body excludes it. */
  void add(final SyntaxNode name, final AccessKind kind, final List<VariableAccess> out) {
    final String text = PythonSyntax.firstText(name);
    if (!excluded.contains(text)) {
      out.add(new VariableAccess(text, kind, name.span(), name.span()));
    }
  }

  private void walrus(final SyntaxNode node, final List<VariableAccess> out) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == PythonSyntax.THREE_PARTS && PythonSyntax.isToken(parts.get(1), WALRUS)) {
      reads(parts.get(2), out);
      writeNames(parts.get(0), out);
    } else {
      for (final SyntaxNode child : parts) {
        reads(child, out);
      }
    }
  }

  /** An attribute name is not a variable; a subscript or call reads its operands. */
  private void trailer(final SyntaxNode node, final List<VariableAccess> out) {
    if (DOT.equals(PythonSyntax.firstText(node))) {
      return;
    }
    for (final SyntaxNode child : node.children()) {
      reads(child, out);
    }
  }

  /** A keyword argument names a parameter of the callee, so only its value is read. */
  private void keywordArgument(final SyntaxNode node, final List<VariableAccess> out) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == PythonSyntax.THREE_PARTS && PythonSyntax.isToken(parts.get(1), EQUALS)) {
      reads(parts.get(2), out);
    } else {
      for (final SyntaxNode child : parts) {
        reads(child, out);
      }
    }
  }

  private static boolean hasTrailer(final SyntaxNode node) {
    if (OTHER_SCOPE.contains(node.kind())) {
      return false;
    }
    return TRAILER.equals(node.kind())
        || node.children().stream().anyMatch(PythonNameAccesses::hasTrailer);
  }

  private static void collectNames(final SyntaxNode node, final List<SyntaxNode> names) {
    if (NAME.equals(node.kind())) {
      names.add(node);
      return;
    }
    for (final SyntaxNode child : node.children()) {
      collectNames(child, names);
    }
  }
}
