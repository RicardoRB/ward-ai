package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the parts of a class body that the slot checks need: slots, class names and bases. */
final class SlotsSyntax {

  private static final int ASSIGNMENT_PARTS = 3;
  private static final String EXPR_STMT = "expr_stmt";
  static final String SLOTS = "__slots__";
  static final String DICT = "__dict__";
  static final Set<String> SCOPES = Set.of("funcdef", "async_funcdef", "lambdef", "classdef");
  private static final Pattern STRING =
      Pattern.compile("^(?:[rRbBuU]?)(['\"])(.*)\\1$", Pattern.DOTALL);
  private static final String STMT = "stmt";
  private static final String NAME_KIND = "name";
  private static final String IDENTIFIER_KIND = "NAME";

  private SlotsSyntax() {}

  /** The value of the class-level {@code __slots__ = ...} statement, or null. */
  static SyntaxNode slotsValue(final SyntaxNode body) {
    for (final SyntaxNode statement : body.children()) {
      final SyntaxNode simple = unwrap(statement);
      if (simple != null
          && simple.children().size() == ASSIGNMENT_PARTS
          && SLOTS.equals(SyntaxWalk.text(simple.children().get(0)))
          && "=".equals(SyntaxWalk.text(simple.children().get(1)))) {
        return simple.children().get(2);
      }
    }
    return null;
  }

  /** The expression statement inside a block statement, or null. */
  static SyntaxNode unwrap(final SyntaxNode statement) {
    return SyntaxWalk.visits(statement, Set.of("expr_stmt")).stream()
        .filter(
            visit -> visit.ancestors().stream().skip(1).noneMatch(a -> SCOPES.contains(a.kind())))
        .findFirst()
        .map(SyntaxWalk.Visit::node)
        .orElse(null);
  }

  /** The names bound in the class body outside its methods: assignments, methods and classes. */
  static Set<String> classLevelNames(final SyntaxNode body) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(body, Set.of("expr_stmt", "funcdef", "classdef"))) {
      if (visit.ancestors().stream().anyMatch(a -> SCOPES.contains(a.kind()))) {
        continue;
      }
      final SyntaxNode node = visit.node();
      if (EXPR_STMT.equals(node.kind())) {
        if (node.children().size() >= ASSIGNMENT_PARTS
            && "=".equals(SyntaxWalk.text(node.children().get(1)))) {
          names.add(SyntaxWalk.text(node.children().get(0)));
        }
      } else {
        definitionName(node).ifPresent(names::add);
      }
    }
    return names;
  }

  /** The name node text of a function or class, if it has one. */
  private static java.util.Optional<String> definitionName(final SyntaxNode definition) {
    return definition.children().stream()
        .filter(child -> NAME_KIND.equals(child.kind()) || IDENTIFIER_KIND.equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text);
  }

  /** Whether the class header names a base class. */
  static boolean hasBases(final SyntaxNode classNode) {
    final String text = SyntaxWalk.text(classNode);
    final int open = text.indexOf('(');
    final int colon = text.indexOf(':');
    return open >= 0 && open < colon && !text.substring(open + 1, text.indexOf(')')).isEmpty();
  }

  /** The string literal leaves under a node, in source order. */
  static List<SyntaxNode> stringLeaves(final SyntaxNode node) {
    final List<SyntaxNode> strings = new ArrayList<>();
    collectStrings(node, strings);
    return strings;
  }

  private static void collectStrings(final SyntaxNode node, final List<SyntaxNode> strings) {
    if (node.children().isEmpty()) {
      if (STRING.matcher(SyntaxWalk.text(node)).matches()) {
        strings.add(node);
      }
      return;
    }
    node.children().forEach(child -> collectStrings(child, strings));
  }

  /** The text of a string literal without its quotes. */
  static String unquote(final String text) {
    final Matcher matcher = STRING.matcher(text);
    return matcher.matches() ? matcher.group(2) : text;
  }

  /** Whether a node is a block of statements. */
  static boolean isBlockNode(final SyntaxNode node) {
    return "block".equals(node.kind());
  }

  /** The statements of a class body block. */
  static List<SyntaxNode> statements(final SyntaxNode body) {
    return body.children().stream().filter(child -> STMT.equals(child.kind())).toList();
  }
}
