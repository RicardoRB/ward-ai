package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reports annotations on a package declaration outside {@code package-info.java}. */
public final class PackageAnnotationCheck implements UnitCheck {

  private static final String PACKAGE = "packageDeclaration";
  private static final String PACKAGE_INFO = "package-info.java";
  private static final String ANNOTATION = "annotation";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message
   */
  public PackageAnnotationCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    if (PACKAGE_INFO.equals(file.name())) {
      return List.of();
    }
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(PACKAGE))) {
      visit.node().children().stream()
          .filter(annotation -> ANNOTATION.equals(annotation.kind()))
          .forEach(annotation -> matches.add(CheckSupport.at(annotation.span(), message)));
    }
    return List.copyOf(matches);
  }
}
