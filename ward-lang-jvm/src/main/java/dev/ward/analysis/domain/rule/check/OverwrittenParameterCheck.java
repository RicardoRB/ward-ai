package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a method whose first statement assigns one of its parameters a value that does not read
 * the parameter, so the value the caller passed is never used.
 */
public final class OverwrittenParameterCheck implements UnitCheck {
  private static final String METHOD = "methodDeclaration";
  private static final String PARAMETER = "formalParameter";
  private static final String IDENTIFIER = "identifier";
  private static final String BODY = "methodBody";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the method
   */
  public OverwrittenParameterCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(METHOD))) {
      final SyntaxNode method = visit.node();
      final String body = bodyText(method);
      if (parameterNames(method).stream().anyMatch(name -> overwritesFirst(body, name))) {
        matches.add(CheckSupport.at(method.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean overwritesFirst(final String body, final String name) {
    final String start = "{" + name + "=";
    if (!body.startsWith(start) || body.startsWith(start + "=")) {
      return false;
    }
    final int end = body.indexOf(';');
    return end > start.length() && !body.substring(start.length(), end).contains(name);
  }

  private static String bodyText(final SyntaxNode method) {
    return method.children().stream()
        .filter(child -> BODY.equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  private static List<String> parameterNames(final SyntaxNode method) {
    return SyntaxWalk.descendants(method).stream()
        .filter(node -> PARAMETER.equals(node.kind()))
        .map(OverwrittenParameterCheck::lastName)
        .filter(name -> !name.isEmpty())
        .toList();
  }

  private static String lastName(final SyntaxNode parameter) {
    final List<SyntaxNode> names =
        SyntaxWalk.descendants(parameter).stream()
            .filter(node -> IDENTIFIER.equals(node.kind()))
            .toList();
    return names.isEmpty() ? "" : SyntaxWalk.text(names.get(names.size() - 1));
  }
}
