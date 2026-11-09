package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.ir.VariableAccesses;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the reads, writes and declarations of the names one Python body binds. A name the body
 * declares {@code global} or {@code nonlocal} is not recorded, and neither are the names of nested
 * functions, classes, lambdas and comprehensions, which have scopes of their own. A {@code del} or
 * an annotation without a value is a {@link AccessKind#DECLARE}, which unbinds the name.
 */
final class PythonAccesses implements VariableAccesses {

  private static final String NAME = "name";
  private static final String TRAILER = "trailer";
  private static final String ARGUMENT = "argument";
  private static final String NAMED_EXPR = "namedexpr_test";
  private static final String EXPR_STMT = "expr_stmt";
  private static final String ANNASSIGN = "annassign";
  private static final String AUGASSIGN = "augassign";
  private static final String DEL = "del_stmt";
  private static final String IMPORT_NAME = "import_name";
  private static final String IMPORT_FROM = "import_from";
  private static final String ALIAS_AS = "as";
  private static final int THREE_PARTS = 3;
  private static final String EQUALS = "=";
  private static final String WALRUS = ":=";
  private static final String DOT = ".";

  /** Nodes that bind or use names of another scope, which this body does not read or write. */
  private static final Set<String> OTHER_SCOPE =
      Set.of(
          "funcdef", "classdef", "decorated", "lambdef", "lambdef_nocond", "comp_for", "comp_if");

  private static final Set<String> IMPORT_ALIASES = Set.of("import_as_name", "dotted_as_name");
  private static final Set<String> NOTHING = Set.of("global_stmt", "nonlocal_stmt", "pass_stmt");

  private final Set<String> excluded;
  private final boolean tracked;
  private final Map<SyntaxNode, List<VariableAccess>> planned = new IdentityHashMap<>();

  /**
   * Creates the finder of one body.
   *
   * @param excluded the names the body declares {@code global} or {@code nonlocal}
   * @param tracked false when the body has a star import, so no name of it is known to be local
   */
  PythonAccesses(final Set<String> excluded, final boolean tracked) {
    this.excluded = Set.copyOf(excluded);
    this.tracked = tracked;
  }

  /**
   * Plans the accesses of a node that the lowering adds with a fixed role, such as the target of a
   * {@code for} loop, which writes its names, or a loop iterable, which reads them.
   *
   * @param node the node the lowering adds next
   * @param writes true when the names of the node are written, false when they are read
   */
  void plan(final SyntaxNode node, final boolean writes) {
    final List<VariableAccess> found = new ArrayList<>();
    if (writes) {
      targets(node, found);
    } else {
      reads(node, found);
    }
    planned.put(node, found);
  }

  @Override
  public List<VariableAccess> of(final SyntaxNode node) {
    if (!tracked) {
      return List.of();
    }
    final List<VariableAccess> found = planned.get(node);
    if (found != null) {
      return found;
    }
    final List<VariableAccess> classified = new ArrayList<>();
    classify(node, classified);
    return classified;
  }

  private void classify(final SyntaxNode node, final List<VariableAccess> out) {
    if (NOTHING.contains(node.kind())) {
      return;
    }
    switch (node.kind()) {
      case EXPR_STMT -> exprStatement(node, out);
      case DEL -> declare(node.children().get(node.children().size() - 1), out);
      case IMPORT_NAME, IMPORT_FROM -> importBindings(node, out);
      case "funcdef", "classdef" -> definition(node, out);
      case "decorated" -> decorated(node, out);
      case "except_clause" -> handler(node, out);
      case "with_item" -> withItem(node, out);
      case "patterns" -> writeNames(node, out);
      default -> reads(node, out);
    }
  }

  private void exprStatement(final SyntaxNode node, final List<VariableAccess> out) {
    final List<SyntaxNode> parts = node.children();
    final SyntaxNode second = parts.size() > 1 ? parts.get(1) : null;
    if (second != null && ANNASSIGN.equals(second.kind())) {
      annotated(parts.get(0), second, out);
    } else if (second != null && AUGASSIGN.equals(second.kind())) {
      reads(parts.get(0), out);
      targets(parts.get(0), out);
      reads(parts.get(2), out);
    } else {
      assignment(node, out);
    }
  }

  /** An assignment reads its value before it writes the targets, so {@code x = x + 1} reads x. */
  private void assignment(final SyntaxNode node, final List<VariableAccess> out) {
    final List<SyntaxNode> parts = node.children();
    final List<Integer> equals = indicesOfToken(parts, EQUALS);
    if (equals.isEmpty()) {
      reads(node, out);
      return;
    }
    final int valueStart = equals.get(equals.size() - 1) + 1;
    for (final SyntaxNode child : parts.subList(valueStart, parts.size())) {
      reads(child, out);
    }
    int start = 0;
    for (final int equal : equals) {
      for (final SyntaxNode child : parts.subList(start, equal)) {
        targets(child, out);
      }
      start = equal + 1;
    }
  }

  private static List<Integer> indicesOfToken(final List<SyntaxNode> parts, final String text) {
    final List<Integer> indices = new ArrayList<>();
    for (int index = 0; index < parts.size(); index++) {
      if (isToken(parts.get(index), text)) {
        indices.add(index);
      }
    }
    return indices;
  }

  private void annotated(
      final SyntaxNode target, final SyntaxNode annotation, final List<VariableAccess> out) {
    final List<SyntaxNode> parts = annotation.children();
    final int equals = indexOfToken(parts, EQUALS);
    if (equals < 0) {
      declare(target, out);
      return;
    }
    for (final SyntaxNode value : parts.subList(equals + 1, parts.size())) {
      reads(value, out);
    }
    targets(target, out);
  }

  private void definition(final SyntaxNode node, final List<VariableAccess> out) {
    for (final SyntaxNode child : node.children()) {
      if (NAME.equals(child.kind())) {
        add(child, AccessKind.WRITE, out);
        return;
      }
    }
  }

  private void decorated(final SyntaxNode node, final List<VariableAccess> out) {
    for (final SyntaxNode child : node.children()) {
      if ("funcdef".equals(child.kind()) || "classdef".equals(child.kind())) {
        definition(child, out);
      }
    }
  }

  private void handler(final SyntaxNode node, final List<VariableAccess> out) {
    final List<SyntaxNode> parts = node.children();
    final int as = indexOfToken(parts, ALIAS_AS);
    for (int index = 0; index < parts.size(); index++) {
      if (as >= 0 && index == as + 1) {
        add(parts.get(index), AccessKind.WRITE, out);
      } else if (index != as + 1) {
        reads(parts.get(index), out);
      }
    }
  }

  private void withItem(final SyntaxNode node, final List<VariableAccess> out) {
    final List<SyntaxNode> parts = node.children();
    reads(parts.get(0), out);
    if (parts.size() == THREE_PARTS) {
      targets(parts.get(2), out);
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
    final int as = indexOfToken(parts, ALIAS_AS);
    if (as >= 0) {
      add(parts.get(as + 1), AccessKind.WRITE, out);
      return;
    }
    final List<SyntaxNode> names = new ArrayList<>();
    collectNames(alias, names);
    if (!names.isEmpty()) {
      add(names.get(0), AccessKind.WRITE, out);
    }
  }

  /** A target that holds a subscript, call or attribute uses its names rather than binding them. */
  private void targets(final SyntaxNode node, final List<VariableAccess> out) {
    if (hasTrailer(node)) {
      reads(node, out);
    } else {
      writeNames(node, out);
    }
  }

  private void declare(final SyntaxNode node, final List<VariableAccess> out) {
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

  private void writeNames(final SyntaxNode node, final List<VariableAccess> out) {
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

  /** Records the names a node reads, in evaluation order. */
  private void reads(final SyntaxNode node, final List<VariableAccess> out) {
    if (NAME.equals(node.kind())) {
      add(node, AccessKind.READ, out);
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

  private void walrus(final SyntaxNode node, final List<VariableAccess> out) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == THREE_PARTS && isToken(parts.get(1), WALRUS)) {
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
    if (DOT.equals(firstText(node))) {
      return;
    }
    for (final SyntaxNode child : node.children()) {
      reads(child, out);
    }
  }

  /** A keyword argument names a parameter of the callee, so only its value is read. */
  private void keywordArgument(final SyntaxNode node, final List<VariableAccess> out) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == THREE_PARTS && isToken(parts.get(1), EQUALS)) {
      reads(parts.get(2), out);
    } else {
      for (final SyntaxNode child : parts) {
        reads(child, out);
      }
    }
  }

  private void add(final SyntaxNode name, final AccessKind kind, final List<VariableAccess> out) {
    final String text = PythonSyntax.text(PythonSyntax.tokens(name).get(0));
    if (!excluded.contains(text)) {
      out.add(new VariableAccess(text, kind, name.span(), name.span()));
    }
  }

  private static boolean hasTrailer(final SyntaxNode node) {
    if (OTHER_SCOPE.contains(node.kind())) {
      return false;
    }
    return TRAILER.equals(node.kind())
        || node.children().stream().anyMatch(PythonAccesses::hasTrailer);
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

  private static String firstText(final SyntaxNode node) {
    final List<SyntaxNode> tokens = PythonSyntax.tokens(node);
    return tokens.isEmpty() ? "" : PythonSyntax.text(tokens.get(0));
  }

  private static boolean isToken(final SyntaxNode node, final String text) {
    return node.children().isEmpty() && text.equals(PythonSyntax.text(node));
  }

  private static int indexOfToken(final List<SyntaxNode> parts, final String text) {
    for (int index = 0; index < parts.size(); index++) {
      if (isToken(parts.get(index), text)) {
        return index;
      }
    }
    return -1;
  }
}
