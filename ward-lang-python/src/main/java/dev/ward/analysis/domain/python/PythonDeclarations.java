package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The declarations of one Python tree, keyed by the start of the declared name: the annotation and
 * the initializer of each variable, each function definition, and the names of the classes.
 *
 * @param nodes the nodes by exact span
 * @param annotations the annotation expressions of parameters and variables
 * @param initializers the expressions assigned to a single-target variable
 * @param functions the function definitions
 * @param classNames the simple names of the classes the file defines
 */
record PythonDeclarations(
    Map<String, SyntaxNode> nodes,
    Map<String, SyntaxNode> annotations,
    Map<String, SyntaxNode> initializers,
    Map<String, SyntaxNode> functions,
    Set<String> classNames) {

  private static final String COMMENT = PythonTypeKinds.COMMENT;
  private static final String EQUALS = "=";
  private static final String COLON = ":";
  private static final int ANNOTATION_INDEX = 2;
  private static final int PARAMETER_PARTS = 3;
  private static final int ASSIGNMENT_PARTS = 3;
  private static final int ANNOTATED_PARTS = 2;
  private static final int ANNASSIGN_WITH_VALUE = 4;
  private static final int FUNCTION_NAME = 1;

  /** Collects the declarations of {@code tree}. */
  static PythonDeclarations of(final SyntaxNode tree) {
    final PythonDeclarations found =
        new PythonDeclarations(
            new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashSet<>());
    collect(tree, found);
    return found;
  }

  private static void collect(final SyntaxNode node, final PythonDeclarations found) {
    if (COMMENT.equals(node.kind())) {
      return;
    }
    found.nodes().putIfAbsent(UnitTypeFacts.key(node.span()), node);
    index(node, found);
    node.children().forEach(child -> collect(child, found));
  }

  private static void index(final SyntaxNode node, final PythonDeclarations found) {
    switch (node.kind()) {
      case PythonTypeKinds.TFPDEF -> indexParameter(node, found);
      case PythonTypeKinds.EXPR_STMT -> indexAssignment(node, found);
      case PythonTypeKinds.FUNCDEF ->
          found.functions().put(startKey(node.children().get(FUNCTION_NAME)), node);
      case PythonTypeKinds.CLASSDEF ->
          found.classNames().add(PythonNames.identifier(node.children().get(FUNCTION_NAME)));
      default -> {
        // Only the declaring nodes above carry declarations.
      }
    }
  }

  private static void indexParameter(final SyntaxNode node, final PythonDeclarations found) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == PARAMETER_PARTS) {
      found.annotations().put(startKey(parts.get(0)), parts.get(ANNOTATION_INDEX));
    }
  }

  private static void indexAssignment(final SyntaxNode node, final PythonDeclarations found) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == ASSIGNMENT_PARTS && PythonTypeKinds.isToken(parts.get(1), EQUALS)) {
      plainName(parts.get(0))
          .ifPresent(target -> found.initializers().put(startKey(target), parts.get(2)));
    } else if (parts.size() == ANNOTATED_PARTS
        && PythonTypeKinds.ANNASSIGN.equals(parts.get(1).kind())) {
      indexAnnotated(parts.get(0), parts.get(1).children(), found);
    }
  }

  private static void indexAnnotated(
      final SyntaxNode target, final List<SyntaxNode> annassign, final PythonDeclarations found) {
    plainName(target)
        .ifPresent(
            name -> {
              found.annotations().put(startKey(name), annassign.get(1));
              if (annassign.size() == ANNASSIGN_WITH_VALUE) {
                found.initializers().put(startKey(name), annassign.get(ANNASSIGN_WITH_VALUE - 1));
              }
            });
  }

  private static java.util.Optional<SyntaxNode> plainName(final SyntaxNode node) {
    final SyntaxNode inner = PythonTypeKinds.descend(node);
    return PythonTypeKinds.isName(inner)
        ? java.util.Optional.of(inner)
        : java.util.Optional.empty();
  }

  /** The key of a declaration named by {@code name}. */
  static String startKey(final SyntaxNode name) {
    return UnitTypeFacts.startKey(name.span());
  }
}
