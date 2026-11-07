package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports the module imports beyond the allowed number, in source order. A module named in {@code
 * excludes} is allowed and does not count. The default allows none.
 */
public final class AvoidModuleImportCheck implements RuleCheck {

  private final Set<String> excludes;
  private final int maxAllowed;
  private final String message;

  /**
   * Creates the check.
   *
   * @param excludes the modules that may be imported
   * @param maxAllowed how many other module imports may appear
   * @param message the message; {@code {value}} is the module name
   */
  public AvoidModuleImportCheck(
      final Set<String> excludes, final int maxAllowed, final String message) {
    this.excludes = Set.copyOf(excludes);
    this.maxAllowed = maxAllowed;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    int counted = 0;
    for (final ImportDeclaration declaration : ImportDeclaration.modules(root)) {
      if (!excludes.contains(declaration.name())) {
        counted++;
        if (counted > maxAllowed) {
          matches.add(
              CheckSupport.at(
                  declaration.span(), CheckSupport.fill(message, declaration.name(), "")));
        }
      }
    }
    return List.copyOf(matches);
  }
}
