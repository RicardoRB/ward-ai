package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports the second of two {@code @Assisted} parameters that have the same type and the same
 * {@code @Assisted} value, so Guice cannot tell them apart, as Error Prone's
 * GuiceAssistedParameters does.
 */
public final class JavaAssistedParametersCheck implements RuleCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the parameter type
   */
  public JavaAssistedParametersCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("formalParameters"))) {
      duplicates(parameters(visit.node()))
          .forEach(
              parameter ->
                  matches.add(
                      CheckSupport.at(
                          parameter.span(), CheckSupport.fill(message, typeOf(parameter), ""))));
    }
    return List.copyOf(matches);
  }

  private static List<SyntaxNode> parameters(final SyntaxNode list) {
    final List<SyntaxNode> parameters = new ArrayList<>(JavaNodes.kids(list, "formalParameter"));
    JavaNodes.kids(list, "formalParameterList")
        .forEach(rest -> parameters.addAll(JavaNodes.kids(rest, "formalParameter")));
    return parameters;
  }

  private static List<SyntaxNode> duplicates(final List<SyntaxNode> parameters) {
    final Set<String> seen = new HashSet<>();
    return parameters.stream()
        .filter(parameter -> !key(parameter).isEmpty() && !seen.add(key(parameter)))
        .toList();
  }

  private static String typeOf(final SyntaxNode parameter) {
    return JavaNodes.kid(parameter, "typeType").map(SyntaxWalk::text).orElse("");
  }

  private static String key(final SyntaxNode parameter) {
    return JavaAnnotations.of(parameter).stream()
        .filter(annotation -> "Assisted".equals(JavaAnnotations.simpleName(annotation)))
        .findFirst()
        .map(annotation -> SyntaxWalk.text(annotation) + "|" + typeOf(parameter))
        .orElse("");
  }
}
