package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each type declared in a {@code package-info.java} file, which is meant for annotations.
 */
public final class PackageInfoTypesCheck implements UnitCheck {

  private static final String FILE = "package-info.java";
  private static final String TYPE = "typeDeclaration";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the type
   */
  public PackageInfoTypesCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    if (!file.path().endsWith(FILE)) {
      return List.of();
    }
    return SyntaxWalk.visits(root, Set.of(TYPE)).stream()
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }
}
