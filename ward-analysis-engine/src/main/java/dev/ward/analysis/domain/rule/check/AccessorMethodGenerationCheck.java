package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports a call outside a nested class of one of that class's private methods, which needs an
 * accessor method before Java 11. Calls are matched by name, so another class's method of the same
 * name counts.
 */
public final class AccessorMethodGenerationCheck implements UnitCheck {

  private static final String METHOD_CALL = "methodCall";
  private static final String IDENTIFIER = "identifier";

  private final int release;
  private final String message;

  /**
   * Creates the check.
   *
   * @param release the Java release, or zero when unknown
   * @param message the message; {@code {value}} is the method name
   */
  public AccessorMethodGenerationCheck(final int release, final String message) {
    this.release = release;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    if (!AccessorRelease.applies(release)) {
      return List.of();
    }
    final List<RuleMatch> matches = new ArrayList<>();
    final List<SyntaxNode> calls =
        SyntaxWalk.descendants(root).stream()
            .filter(node -> METHOD_CALL.equals(node.kind()))
            .toList();
    for (final SyntaxNode nested : AccessorRelease.nestedClasses(root)) {
      final Set<String> names = privateMethodNames(nested);
      final Set<SyntaxNode> inside = Set.copyOf(SyntaxWalk.descendants(nested));
      calls.stream()
          .filter(call -> names.contains(calledName(call)) && !inside.contains(call))
          .forEach(
              call ->
                  matches.add(
                      CheckSupport.at(
                          call.span(), CheckSupport.fill(message, calledName(call), ""))));
    }
    return List.copyOf(matches);
  }

  private static Set<String> privateMethodNames(final SyntaxNode nested) {
    return JUnitMembers.body(nested).stream()
        .flatMap(body -> JUnitMembers.members(body).stream())
        .filter(JUnitMembers::isPrivate)
        .flatMap(member -> JUnitMembers.method(member).stream())
        .map(JUnitMembers::name)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static String calledName(final SyntaxNode call) {
    return call.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }
}
