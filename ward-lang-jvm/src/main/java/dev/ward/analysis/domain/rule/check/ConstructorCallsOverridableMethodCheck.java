package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports a call, in a constructor of a non-final class, to a method of that class that a subclass
 * can override, as PMD's ConstructorCallsOverridableMethod does. A call is on this object when it
 * has no receiver or {@code this}; a method is overridable when the class declares it without
 * {@code private}, {@code static} or {@code final}. PMD also follows calls through other methods,
 * which this check does not.
 */
public final class ConstructorCallsOverridableMethodCheck implements UnitCheck {

  private static final String METHOD_CALL = "methodCall";
  private static final String EXPRESSION = "expression";
  private static final String THIS = "this";
  private static final Set<String> NESTED = Set.of("lambdaExpression", "classBody");
  private static final int QUALIFIED_PARTS = 3;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call; {@code {value}} is the method name
   */
  public ConstructorCallsOverridableMethodCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .filter(visit -> !isFinal(visit.ancestors()))
        .map(SyntaxWalk.Visit::node)
        .flatMap(clazz -> reportsOf(clazz).stream())
        .toList();
  }

  private static boolean isFinal(final List<SyntaxNode> ancestors) {
    return MemberDeclarations.wrapper(ancestors).stream()
        .anyMatch(
            wrapper -> MemberDeclarations.modifiers(wrapper).contains(MemberDeclarations.FINAL));
  }

  private List<RuleMatch> reportsOf(final SyntaxNode clazz) {
    final Set<String> overridable =
        ClassMembers.members(clazz).stream()
            .filter(ConstructorCallsOverridableMethodCheck::isOverridable)
            .map(member -> MemberDeclarations.name(ClassMembers.declarationOf(member)))
            .collect(Collectors.toSet());
    final List<RuleMatch> reports = new ArrayList<>();
    ClassMembers.members(clazz).stream()
        .map(ClassMembers::declarationOf)
        .filter(declaration -> MemberDeclarations.CONSTRUCTOR.equals(declaration.kind()))
        .forEach(constructor -> collect(constructor, overridable, reports));
    return reports;
  }

  private void collect(
      final SyntaxNode node, final Set<String> overridable, final List<RuleMatch> reports) {
    for (final SyntaxNode child : node.children()) {
      if (NESTED.contains(child.kind())) {
        continue;
      }
      if (EXPRESSION.equals(child.kind())) {
        callOn(child, overridable).ifPresent(reports::add);
      }
      collect(child, overridable, reports);
    }
  }

  /** Returns a match when {@code expression} is a call of an overridable method on this object. */
  private Optional<RuleMatch> callOn(final SyntaxNode expression, final Set<String> overridable) {
    final List<SyntaxNode> parts = expression.children();
    final boolean bare = parts.size() == 1 && METHOD_CALL.equals(parts.get(0).kind());
    final boolean viaThis =
        parts.size() == QUALIFIED_PARTS
            && THIS.equals(SyntaxWalk.text(parts.get(0)))
            && METHOD_CALL.equals(parts.get(2).kind());
    if (!bare && !viaThis) {
      return Optional.empty();
    }
    final String name = MemberDeclarations.name(parts.get(parts.size() - 1));
    return overridable.contains(name)
        ? Optional.of(CheckSupport.at(expression.span(), CheckSupport.fill(message, name, "")))
        : Optional.empty();
  }

  private static boolean isOverridable(final SyntaxNode member) {
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    return MemberDeclarations.METHOD.equals(ClassMembers.declarationOf(member).kind())
        && !modifiers.contains(MemberDeclarations.PRIVATE)
        && !modifiers.contains(MemberDeclarations.STATIC)
        && !modifiers.contains(MemberDeclarations.FINAL);
  }
}
