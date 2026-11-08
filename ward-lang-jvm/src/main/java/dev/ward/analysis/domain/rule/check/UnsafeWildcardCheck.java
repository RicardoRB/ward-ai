package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a {@code null} that is given a wildcard type javac cannot make sense of, and the wildcard
 * types that are unsafe by themselves: a lower bound that is an unbounded type variable where the
 * generic type of the file bounds the parameter it fills, as in {@code Box<? super U>} for {@code
 * class Box<T extends Number>}. The generic types must be declared in the same file; a type of
 * another file has unknown bounds and is never reported. It looks at declarations with a {@code
 * null} initializer, uninitialized non-final fields, {@code extends} and {@code implements}
 * clauses, {@code return null} and casts of {@code null}.
 */
public final class UnsafeWildcardCheck implements RuleCheck {

  private static final String TYPE_TYPE = "typeType";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the unsafe type or null
   */
  public UnsafeWildcardCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final WildcardTypes types = new WildcardTypes(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(
            root,
            Set.of(
                "fieldDeclaration",
                "localVariableDeclaration",
                "classDeclaration",
                "statement",
                "expression"))) {
      found.addAll(look(visit, types));
    }
    return List.copyOf(found);
  }

  private List<RuleMatch> look(final SyntaxWalk.Visit visit, final WildcardTypes types) {
    final SyntaxNode node = visit.node();
    return switch (node.kind()) {
      case "fieldDeclaration", "localVariableDeclaration" -> declaration(node, types);
      case "classDeclaration" -> supertypes(node, types);
      case "statement" -> returned(visit, types);
      default -> cast(node, types);
    };
  }

  private List<RuleMatch> declaration(final SyntaxNode node, final WildcardTypes types) {
    final Optional<SyntaxNode> type = NullSyntax.first(node, TYPE_TYPE).filter(types::unsafe);
    if (type.isEmpty()) {
      return List.of();
    }
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxNode declarator : SyntaxWalk.descendants(node)) {
      if ("variableDeclarator".equals(declarator.kind())) {
        initializer(declarator)
            .ifPresentOrElse(
                value -> {
                  if ("null".equals(SyntaxWalk.text(value))) {
                    found.add(CheckSupport.at(value.span(), message));
                  }
                },
                () -> {
                  if ("fieldDeclaration".equals(node.kind())) {
                    found.add(CheckSupport.at(type.get().span(), message));
                  }
                });
      }
    }
    return found;
  }

  private static Optional<SyntaxNode> initializer(final SyntaxNode declarator) {
    return NullSyntax.first(declarator, "variableInitializer")
        .flatMap(init -> NullSyntax.first(init, "expression"));
  }

  private List<RuleMatch> supertypes(final SyntaxNode node, final WildcardTypes types) {
    return node.children().stream()
        .flatMap(
            child ->
                "typeList".equals(child.kind())
                    ? child.children().stream()
                    : java.util.stream.Stream.of(child))
        .filter(child -> TYPE_TYPE.equals(child.kind()) && types.unsafe(child))
        .map(child -> CheckSupport.at(child.span(), message))
        .toList();
  }

  private List<RuleMatch> returned(final SyntaxWalk.Visit visit, final WildcardTypes types) {
    final List<SyntaxNode> parts = visit.node().children();
    if (parts.size() < 2
        || !"return".equals(SyntaxWalk.text(parts.get(0)))
        || !"null".equals(SyntaxWalk.text(parts.get(1)))) {
      return List.of();
    }
    final boolean unsafe =
        visit.ancestors().stream()
            .filter(ancestor -> "methodDeclaration".equals(ancestor.kind()))
            .reduce((first, second) -> second)
            .flatMap(method -> NullSyntax.first(method, "typeTypeOrVoid"))
            .flatMap(returns -> NullSyntax.first(returns, TYPE_TYPE))
            .filter(types::unsafe)
            .isPresent();
    return unsafe ? List.of(CheckSupport.at(parts.get(1).span(), message)) : List.of();
  }

  private List<RuleMatch> cast(final SyntaxNode node, final WildcardTypes types) {
    final List<SyntaxNode> parts = node.children();
    final boolean unsafe =
        parts.size() == 4
            && "(".equals(SyntaxWalk.text(parts.get(0)))
            && TYPE_TYPE.equals(parts.get(1).kind())
            && "null".equals(SyntaxWalk.text(parts.get(3)))
            && types.unsafe(parts.get(1));
    return unsafe ? List.of(CheckSupport.at(parts.get(3).span(), message)) : List.of();
  }
}
