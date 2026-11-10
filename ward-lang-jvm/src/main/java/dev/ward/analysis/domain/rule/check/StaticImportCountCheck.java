package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;

/**
 * Reports a file that has more than {@code max} static imports, as PMD's TooManyStaticImports does.
 */
public final class StaticImportCountCheck implements RuleCheck {

  private static final String IMPORT = "importDeclaration";
  private static final String STATIC = "'static'";

  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the most static imports a file may have
   * @param message the message; {@code {value}} is the count and {@code {max}} the limit
   */
  public StaticImportCountCheck(final int max, final String message) {
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final long count =
        JavaNodes.kids(root, IMPORT).stream()
            .filter(node -> JavaNodes.kid(node, STATIC).isPresent())
            .count();
    return count > max
        ? List.of(
            CheckSupport.at(
                root.span(),
                CheckSupport.fill(message, String.valueOf(count), String.valueOf(max))))
        : List.of();
  }
}
