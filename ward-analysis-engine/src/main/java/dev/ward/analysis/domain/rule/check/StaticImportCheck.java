package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each static import that is not excluded. An exclusion names an import, such as {@code
 * java.lang.System.out}, or a class with a star, such as {@code java.lang.Math.*}, which admits
 * every static member of that class.
 */
public final class StaticImportCheck implements RuleCheck {

  private final Set<String> excludes;
  private final String message;

  /**
   * Creates the check.
   *
   * @param excludes the static imports and classes that are allowed
   * @param message the message; {@code {value}} is the imported name
   */
  public StaticImportCheck(final Set<String> excludes, final String message) {
    this.excludes = Set.copyOf(excludes);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final ImportDeclaration declaration : ImportDeclaration.declarations(root)) {
      if (declaration.isStatic() && !excluded(declaration)) {
        matches.add(
            CheckSupport.at(
                declaration.span(), CheckSupport.fill(message, declaration.fullName(), "")));
      }
    }
    return List.copyOf(matches);
  }

  private boolean excluded(final ImportDeclaration declaration) {
    return excludes.contains(declaration.fullName())
        || excludes.contains(declaration.classPart() + ".*");
  }
}
