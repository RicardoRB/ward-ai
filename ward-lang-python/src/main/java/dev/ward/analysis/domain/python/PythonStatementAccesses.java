package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the accesses of the Python statements that bind or unbind names: assignments, annotations,
 * {@code del}, definitions, imports, {@code except ... as} and {@code with ... as} clauses.
 */
final class PythonStatementAccesses {

  private static final String EXPR_STMT = "expr_stmt";
  private static final String ANNASSIGN = "annassign";
  private static final String AUGASSIGN = "augassign";
  private static final String DEL = "del_stmt";
  private static final String IMPORT_NAME = "import_name";
  private static final String IMPORT_FROM = "import_from";
  private static final String ALIAS_AS = "as";
  private static final String EQUALS = "=";
  private static final String DECORATED = "decorated";
  private static final Set<String> IMPORT_ALIASES = Set.of("import_as_name", "dotted_as_name");

  private final PythonNameAccesses names;

  /**
   * Creates the statement finder over the name finder of one body.
   *
   * @param names the finder of the names of the body
   */
  PythonStatementAccesses(final PythonNameAccesses names) {
    this.names = names;
  }

  /**
   * Records the accesses of a statement that binds names, if {@code node} is one.
   *
   * @param node the statement or expression the lowering adds
   * @param out the accesses, in evaluation order
   * @return false when {@code node} binds no names, so the caller reads its names instead
   */
  boolean classify(final SyntaxNode node, final List<VariableAccess> out) {
    switch (node.kind()) {
      case EXPR_STMT -> exprStatement(node, out);
      case DEL -> names.declare(node.children().get(node.children().size() - 1), out);
      case IMPORT_NAME, IMPORT_FROM -> importBindings(node, out);
      case "funcdef", "classdef", "decorated" -> definition(node, out);
      default -> {
        return clause(node, out);
      }
    }
    return true;
  }

  /**
   * Records the accesses of a clause that binds a name: an except handler, a with item, a pattern.
   */
  private boolean clause(final SyntaxNode node, final List<VariableAccess> out) {
    switch (node.kind()) {
      case "except_clause" -> handler(node, out);
      case "with_item" -> withItem(node, out);
      case "patterns" -> names.writeNames(node, out);
      default -> {
        return false;
      }
    }
    return true;
  }

  private void exprStatement(final SyntaxNode node, final List<VariableAccess> out) {
    final List<SyntaxNode> parts = node.children();
    final SyntaxNode second = parts.size() > 1 ? parts.get(1) : null;
    if (second != null && ANNASSIGN.equals(second.kind())) {
      annotated(parts.get(0), second, out);
    } else if (second != null && AUGASSIGN.equals(second.kind())) {
      names.reads(parts.get(0), out);
      names.targets(parts.get(0), out);
      names.reads(parts.get(2), out);
    } else {
      assignment(node, out);
    }
  }

  /** An assignment reads its value before it writes the targets, so {@code x = x + 1} reads x. */
  private void assignment(final SyntaxNode node, final List<VariableAccess> out) {
    final List<SyntaxNode> parts = node.children();
    final List<Integer> equals = PythonSyntax.indicesOfToken(parts, EQUALS);
    if (equals.isEmpty()) {
      names.reads(node, out);
      return;
    }
    final int valueStart = equals.get(equals.size() - 1) + 1;
    for (final SyntaxNode child : parts.subList(valueStart, parts.size())) {
      names.reads(child, out);
    }
    int start = 0;
    for (final int equal : equals) {
      for (final SyntaxNode child : parts.subList(start, equal)) {
        names.targets(child, out);
      }
      start = equal + 1;
    }
  }

  private void annotated(
      final SyntaxNode target, final SyntaxNode annotation, final List<VariableAccess> out) {
    final List<SyntaxNode> parts = annotation.children();
    final int equals = PythonSyntax.indexOfToken(parts, EQUALS);
    if (equals < 0) {
      names.declare(target, out);
      return;
    }
    for (final SyntaxNode value : parts.subList(equals + 1, parts.size())) {
      names.reads(value, out);
    }
    names.targets(target, out);
  }

  /**
   * A definition binds its name; a decorated one binds the name of the function or class it wraps.
   */
  private void definition(final SyntaxNode node, final List<VariableAccess> out) {
    final Optional<SyntaxNode> name = PythonSyntax.firstOfKind(node, "name");
    if (DECORATED.equals(node.kind())) {
      PythonSyntax.firstOfKind(node, "funcdef")
          .or(() -> PythonSyntax.firstOfKind(node, "classdef"))
          .ifPresent(wrapped -> definition(wrapped, out));
    } else {
      name.ifPresent(found -> names.add(found, AccessKind.WRITE, out));
    }
  }

  private void handler(final SyntaxNode node, final List<VariableAccess> out) {
    final List<SyntaxNode> parts = node.children();
    final int as = PythonSyntax.indexOfToken(parts, ALIAS_AS);
    for (int index = 0; index < parts.size(); index++) {
      if (as >= 0 && index == as + 1) {
        names.add(parts.get(index), AccessKind.WRITE, out);
      } else if (index != as + 1) {
        names.reads(parts.get(index), out);
      }
    }
  }

  private void withItem(final SyntaxNode node, final List<VariableAccess> out) {
    final List<SyntaxNode> parts = node.children();
    names.reads(parts.get(0), out);
    if (parts.size() == PythonSyntax.THREE_PARTS) {
      names.targets(parts.get(2), out);
    }
  }

  private void importBindings(final SyntaxNode node, final List<VariableAccess> out) {
    if (IMPORT_ALIASES.contains(node.kind())) {
      bind(node, out);
      return;
    }
    for (final SyntaxNode child : node.children()) {
      importBindings(child, out);
    }
  }

  /** An alias binds its name after {@code as}; without one the first name of the import binds. */
  private void bind(final SyntaxNode alias, final List<VariableAccess> out) {
    final List<SyntaxNode> parts = alias.children();
    final int as = PythonSyntax.indexOfToken(parts, ALIAS_AS);
    if (as >= 0) {
      names.add(parts.get(as + 1), AccessKind.WRITE, out);
      return;
    }
    PythonSyntax.firstOfKind(alias, "name")
        .ifPresent(found -> names.add(found, AccessKind.WRITE, out));
  }
}
