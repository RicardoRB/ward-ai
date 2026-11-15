package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports an array parameter that is the last one of a method or constructor, where varargs would
 * do. Like PMD's UseVarargs, it leaves {@code byte[]} alone and the {@code main(String[])} method.
 */
public final class ArrayLastParameterCheck implements UnitCheck {
  private static final String COMMA = "','";
  private static final Pattern ARRAY = Pattern.compile("(?<!byte)\\[\\]\\w+$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the parameter's text
   */
  public ArrayLastParameterCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of("formalParameters")).stream()
        .flatMap(
            visit ->
                lastParameter(visit.node())
                    .filter(parameter -> ARRAY.matcher(SyntaxWalk.text(parameter)).find())
                    .filter(parameter -> !isMain(visit))
                    .stream())
        .map(
            parameter ->
                CheckSupport.at(
                    parameter.span(), CheckSupport.fill(message, SyntaxWalk.text(parameter), "")))
        .toList();
  }

  private static Optional<SyntaxNode> lastParameter(final SyntaxNode parameters) {
    final List<SyntaxNode> all =
        SyntaxWalk.descendants(parameters).stream()
            .filter(node -> "formalParameter".equals(node.kind()))
            .toList();
    return all.isEmpty() ? Optional.empty() : Optional.of(all.get(all.size() - 1));
  }

  /** Tells whether the parameters are those of a method named {@code main} with one parameter. */
  private static boolean isMain(final SyntaxWalk.Visit parameters) {
    final List<SyntaxNode> up = parameters.ancestors();
    if (up.isEmpty() || !"methodDeclaration".equals(up.get(up.size() - 1).kind())) {
      return false;
    }
    final boolean named =
        up.get(up.size() - 1).children().stream()
            .anyMatch(
                child ->
                    "identifier".equals(child.kind()) && "main".equals(SyntaxWalk.text(child)));
    return named
        && SyntaxWalk.descendants(parameters.node()).stream()
            .noneMatch(node -> COMMA.equals(node.kind()));
  }
}
