package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reports a file that has at least the minimum number of imports, as PMD's excessive imports. */
public final class ImportCountCheck implements UnitCheck {

  private static final String UNIT = "compilationunit";
  private static final String IMPORT = "importDeclaration";

  private final int minimum;
  private final String message;

  /**
   * Creates the check.
   *
   * @param minimum the import count from which a file is reported
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   */
  public ImportCountCheck(final int minimum, final String message) {
    this.minimum = CheckSupport.nonNegative(minimum, "minimum");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(UNIT)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(unit -> reportOf(unit).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode unit) {
    final long count = unit.children().stream().filter(c -> IMPORT.equals(c.kind())).count();
    if (count < minimum) {
      return List.of();
    }
    return List.of(
        CheckSupport.at(
            unit.span(),
            CheckSupport.fill(message, String.valueOf(count), String.valueOf(minimum))));
  }
}
