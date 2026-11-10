package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports an {@code assert} statement whose condition reads a parameter of the public or protected
 * method or constructor that contains it: arguments of an exposed method must be checked with an
 * exception, because assertions can be switched off.
 */
public final class AssertOnParameterCheck implements UnitCheck {
  private static final String STATEMENT = "statement";
  private static final String ASSERT = "'assert'";
  private static final String IDENTIFIER = "identifier";
  private static final String PARAMETER = "formalParameter";
  private static final String PARAMETER_ID = "variableDeclaratorId";
  private static final Set<String> CALLABLES =
      Set.of("methodDeclaration", "constructorDeclaration");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the assert statement
   */
  public AssertOnParameterCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(STATEMENT)).stream()
        .filter(visit -> isAssert(visit.node()) && exposed(visit.ancestors()))
        .filter(AssertOnParameterCheck::readsParameter)
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private static boolean isAssert(final SyntaxNode statement) {
    return !statement.children().isEmpty() && ASSERT.equals(statement.children().get(0).kind());
  }

  private static boolean exposed(final List<SyntaxNode> ancestors) {
    return DeclaredModifiers.has(ancestors, "public")
        || DeclaredModifiers.has(ancestors, "protected");
  }

  private static boolean readsParameter(final SyntaxWalk.Visit visit) {
    final Optional<SyntaxNode> callable =
        visit.ancestors().stream().filter(a -> CALLABLES.contains(a.kind())).reduce((a, b) -> b);
    if (callable.isEmpty() || visit.node().children().size() < 2) {
      return false;
    }
    final Set<String> parameters = parameterNames(callable.get());
    return SyntaxWalk.descendants(visit.node().children().get(1)).stream()
        .anyMatch(
            node -> IDENTIFIER.equals(node.kind()) && parameters.contains(SyntaxWalk.text(node)));
  }

  private static Set<String> parameterNames(final SyntaxNode callable) {
    return SyntaxWalk.descendants(callable).stream()
        .filter(node -> PARAMETER.equals(node.kind()))
        .flatMap(parameter -> parameter.children().stream())
        .filter(child -> PARAMETER_ID.equals(child.kind()))
        .map(SyntaxWalk::text)
        .collect(Collectors.toUnmodifiableSet());
  }
}
