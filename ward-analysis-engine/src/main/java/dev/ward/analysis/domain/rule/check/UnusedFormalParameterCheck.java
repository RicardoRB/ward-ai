package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports each parameter that a method body never names. Only private methods are checked unless
 * {@code checkAll} is set, which also checks the other methods that have a body.
 */
public final class UnusedFormalParameterCheck implements UnitCheck {

  private final boolean checkAll;
  private final String message;

  /**
   * Creates the check.
   *
   * @param checkAll whether to check methods that are not private
   * @param message the message; {@code {value}} is the parameter name
   */
  public UnusedFormalParameterCheck(final boolean checkAll, final String message) {
    this.checkAll = checkAll;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classBody"))) {
      for (final SyntaxNode member : JUnitTests.members(visit.node())) {
        if (checkAll || JUnitTests.isPrivate(member)) {
          JUnitTests.method(member).ifPresent(method -> unused(method, matches));
        }
      }
    }
    return List.copyOf(matches);
  }

  private void unused(final SyntaxNode method, final List<RuleMatch> matches) {
    final Set<String> named = named(method);
    for (final SyntaxNode parameter : parameters(method)) {
      final String name = parameterName(parameter);
      if (!name.isEmpty() && !named.contains(name)) {
        matches.add(CheckSupport.at(parameter.span(), CheckSupport.fill(message, name, "")));
      }
    }
  }

  private static Set<String> named(final SyntaxNode method) {
    return SyntaxWalk.descendants(method).stream()
        .filter(node -> "methodBody".equals(node.kind()))
        .flatMap(body -> SyntaxWalk.descendants(body).stream())
        .filter(node -> "identifier".equals(node.kind()))
        .map(SyntaxWalk::text)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static List<SyntaxNode> parameters(final SyntaxNode method) {
    return method.children().stream()
        .filter(child -> "formalParameters".equals(child.kind()))
        .flatMap(list -> SyntaxWalk.descendants(list).stream())
        .filter(child -> "formalParameter".equals(child.kind()))
        .toList();
  }

  private static String parameterName(final SyntaxNode parameter) {
    return parameter.children().stream()
        .filter(child -> "variableDeclaratorId".equals(child.kind()))
        .flatMap(id -> id.children().stream())
        .filter(child -> "identifier".equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }
}
