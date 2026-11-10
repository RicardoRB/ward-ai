package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each import that adds nothing: a repeat of an earlier import, a class of {@code
 * java.lang}, or a class of the unit's own package. Star and static imports are reported only when
 * repeated.
 */
public final class RedundantImportCheck implements RuleCheck {

  private static final String JAVA_LANG = "java.lang";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the imported name
   */
  public RedundantImportCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Optional<String> unitPackage = ImportDeclaration.packageOf(root);
    final Set<String> seen = new HashSet<>();
    final List<RuleMatch> matches = new ArrayList<>();
    for (final ImportDeclaration declaration : ImportDeclaration.declarations(root)) {
      final String key = (declaration.isStatic() ? "static " : "") + declaration.fullName();
      if (!seen.add(key) || redundantClass(declaration, unitPackage)) {
        matches.add(
            CheckSupport.at(
                declaration.span(), CheckSupport.fill(message, declaration.fullName(), "")));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean redundantClass(
      final ImportDeclaration declaration, final Optional<String> unitPackage) {
    if (declaration.isStatic() || declaration.star()) {
      return false;
    }
    return JAVA_LANG.equals(declaration.parent())
        || unitPackage.map(declaration.parent()::equals).orElse(false);
  }
}
