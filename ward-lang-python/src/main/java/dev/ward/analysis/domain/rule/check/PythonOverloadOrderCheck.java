package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an {@code @overload} definition whose earlier overloads of the same name do not come
 * right before it in the same class or module, like Checkstyle's {@code
 * OverloadMethodsDeclarationOrder}. Only the overloads count; an implementation or another
 * definition between them is reported.
 */
public final class PythonOverloadOrderCheck implements RuleCheck {

  private static final String STMT = "stmt";
  private static final String COMPOUND = "compound_stmt";
  private static final String DECORATED = "decorated";
  private static final String FUNCDEF = "funcdef";
  private static final int NAME_INDEX = 1;
  private static final int MIN_STATEMENTS = 2;
  private static final String NO_NAME = "";
  private static final Set<String> OVERLOAD_DECORATORS = Set.of("@overload", "@typing.overload");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public PythonOverloadOrderCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    collect(root, found);
    return List.copyOf(found);
  }

  /** Checks every statement sequence below {@code node}: a class body or the module. */
  private void collect(final SyntaxNode node, final List<RuleMatch> found) {
    final List<SyntaxNode> statements = children(node, STMT);
    if (statements.size() >= MIN_STATEMENTS) {
      sequence(statements, found);
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, found);
    }
  }

  /** Reports each overload whose predecessor is not an overload of the same name. */
  private void sequence(final List<SyntaxNode> statements, final List<RuleMatch> found) {
    final Set<String> seen = new HashSet<>();
    String previous = NO_NAME;
    for (final SyntaxNode statement : statements) {
      final Optional<Overload> overload = overload(statement);
      if (overload.isPresent()) {
        final String name = overload.get().name();
        if (seen.contains(name) && !name.equals(previous)) {
          found.add(new RuleMatch(overload.get().span(), message));
        }
        seen.add(name);
        previous = name;
      } else {
        previous = NO_NAME;
      }
    }
  }

  /** The overload that a statement defines, if it is a decorated overloaded function. */
  private static Optional<Overload> overload(final SyntaxNode statement) {
    return children(statement, COMPOUND).stream()
        .flatMap(compound -> children(compound, DECORATED).stream())
        .filter(PythonOverloadOrderCheck::isOverload)
        .flatMap(decorated -> children(decorated, FUNCDEF).stream())
        .findFirst()
        .map(PythonOverloadOrderCheck::overloadOf);
  }

  private static boolean isOverload(final SyntaxNode decorated) {
    return children(decorated, "decorators").stream()
        .map(decorators -> SyntaxWalk.text(decorators).strip())
        .anyMatch(OVERLOAD_DECORATORS::contains);
  }

  private static Overload overloadOf(final SyntaxNode function) {
    final List<SyntaxNode> leaves = SyntaxWalk.leaves(function);
    final String name = leaves.size() > NAME_INDEX ? SyntaxWalk.text(leaves.get(NAME_INDEX)) : "";
    return new Overload(name, function.span());
  }

  private static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (kind.equals(child.kind())) {
        found.add(child);
      }
    }
    return found;
  }

  /** An overload definition: the name it defines and where its function sits. */
  private record Overload(String name, Span span) {}
}
