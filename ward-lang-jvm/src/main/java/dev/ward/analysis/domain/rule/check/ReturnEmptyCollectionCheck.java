package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports {@code return null;} in a method whose declared return type is an array or a well-known
 * collection or map type, as PMD's ReturnEmptyCollectionRatherThanNull does. The type is read from
 * its name, so a user type that extends a collection is not seen. Lambdas and nested classes are
 * not entered, because their returns are not the method's.
 */
public final class ReturnEmptyCollectionCheck implements UnitCheck {

  private static final Set<String> COLLECTIONS =
      Set.of(
          "Collection",
          "List",
          "Set",
          "Map",
          "SortedSet",
          "SortedMap",
          "NavigableSet",
          "NavigableMap",
          "Queue",
          "Deque",
          "ArrayList",
          "LinkedList",
          "HashSet",
          "LinkedHashSet",
          "TreeSet",
          "HashMap",
          "LinkedHashMap",
          "TreeMap",
          "Vector",
          "ArrayDeque");
  private static final Set<String> NESTED = Set.of("lambdaExpression", "classBody");
  private static final String RETURN_NULL = "returnnull;";
  private static final String STATEMENT = "statement";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the return statement
   */
  public ReturnEmptyCollectionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.METHOD)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(ReturnEmptyCollectionCheck::returnsCollection)
        .flatMap(method -> returnsOfNull(method).stream())
        .map(statement -> CheckSupport.at(statement.span(), message))
        .toList();
  }

  private static boolean returnsCollection(final SyntaxNode method) {
    return ClassMembers.typeText(method)
        .map(
            type -> {
              final int generic = type.indexOf('<');
              final String head = generic < 0 ? type : type.substring(0, generic);
              return type.endsWith("]") || COLLECTIONS.contains(ClassMembers.simpleName(head));
            })
        .orElse(false);
  }

  private static List<SyntaxNode> returnsOfNull(final SyntaxNode method) {
    final List<SyntaxNode> found = new ArrayList<>();
    method.children().forEach(child -> collect(child, found));
    return found;
  }

  private static void collect(final SyntaxNode node, final List<SyntaxNode> found) {
    if (NESTED.contains(node.kind())) {
      return;
    }
    if (STATEMENT.equals(node.kind()) && RETURN_NULL.equals(SyntaxWalk.text(node))) {
      found.add(node);
      return;
    }
    node.children().forEach(child -> collect(child, found));
  }
}
