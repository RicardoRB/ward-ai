package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an import statement at the top level of a module that repeats an earlier import of the
 * same module or of the same name from the same module, with the same alias ({@code reimported}).
 * Imports inside functions, classes and conditional blocks are not compared.
 */
public final class ReimportedCheck implements RuleCheck {

  private static final Set<String> IMPORTS = Set.of("import_name", "import_from");
  private static final Set<String> WRAPPERS =
      Set.of(
          "file_input",
          "stmt",
          "compound_stmt",
          "simple_stmts",
          "simple_stmt",
          "small_stmt",
          "import_stmt");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public ReimportedCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> seen = new HashSet<>();
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, IMPORTS)) {
      if (visit.ancestors().stream().allMatch(a -> WRAPPERS.contains(a.kind()))) {
        final String key = keyOf(visit.node());
        if (!seen.add(key)) {
          found.add(new RuleMatch(visit.node().span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  /** The import as written without spaces: the same text means the same binding. */
  private static String keyOf(final SyntaxNode statement) {
    return SyntaxWalk.text(statement);
  }
}
