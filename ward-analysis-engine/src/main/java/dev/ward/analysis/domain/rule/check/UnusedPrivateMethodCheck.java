package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports each private method whose name no call in the file names. The check matches names, not
 * bindings, so an overload or another class's method of the same name counts as a use.
 */
public final class UnusedPrivateMethodCheck implements UnitCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the method name
   */
  public UnusedPrivateMethodCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final Set<String> called = calledNames(root);
    return SyntaxWalk.visits(root, Set.of("classBody")).stream()
        .flatMap(visit -> JUnitTests.members(visit.node()).stream())
        .filter(JUnitTests::isPrivate)
        .flatMap(member -> JUnitTests.method(member).stream())
        .filter(method -> !called.contains(JUnitTests.name(method)))
        .map(
            method ->
                CheckSupport.at(
                    method.span(), CheckSupport.fill(message, JUnitTests.name(method), "")))
        .toList();
  }

  private static Set<String> calledNames(final SyntaxNode root) {
    return SyntaxWalk.descendants(root).stream()
        .filter(node -> "methodCall".equals(node.kind()))
        .flatMap(call -> call.children().stream())
        .filter(child -> "identifier".equals(child.kind()))
        .map(SyntaxWalk::text)
        .collect(Collectors.toUnmodifiableSet());
  }
}
