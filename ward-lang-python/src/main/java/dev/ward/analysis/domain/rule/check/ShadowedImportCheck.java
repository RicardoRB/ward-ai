package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a top-level import whose alias ({@code import x as y}) repeats the name an earlier import
 * already binds, as in {@code import os} followed by {@code import sys as os}. Plain imports of the
 * same name are left to the reimported check.
 */
public final class ShadowedImportCheck implements RuleCheck {

  private static final Set<String> IMPORTS = Set.of("import_name", "import_from");
  private static final Set<String> WRAPPERS =
      Set.of("file_input", "stmt", "simple_stmts", "simple_stmt", "small_stmt", "import_stmt");
  private static final String IMPORT = "import";
  private static final String AS = "as";
  private static final String COMMA = ",";
  private static final int FIRST = 0;
  private static final int ALIAS = 1;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public ShadowedImportCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> bound = new HashSet<>();
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, IMPORTS)) {
      if (visit.ancestors().stream().allMatch(a -> WRAPPERS.contains(a.kind()))) {
        bindStatement(visit.node(), bound, found);
      }
    }
    return List.copyOf(found);
  }

  /** Binds the names of one import statement, reporting an alias that an earlier name occupies. */
  private void bindStatement(
      final SyntaxNode statement, final Set<String> bound, final List<RuleMatch> found) {
    for (final String[] item : items(SyntaxWalk.leaves(statement))) {
      if (item[ALIAS] != null && bound.contains(item[ALIAS])) {
        found.add(new RuleMatch(statement.span(), message));
      }
      bound.add(item[ALIAS] != null ? item[ALIAS] : item[FIRST]);
    }
  }

  /**
   * Splits the leaves after the {@code import} keyword into items, each with its first name and its
   * alias or null. The alias is the name after {@code as}.
   */
  private static List<String[]> items(final List<SyntaxNode> leaves) {
    final List<String[]> out = new ArrayList<>();
    int index = 0;
    while (index < leaves.size() && !IMPORT.equals(SyntaxWalk.text(leaves.get(index)))) {
      index++;
    }
    index++;
    while (index < leaves.size()) {
      final String first = SyntaxWalk.text(leaves.get(index));
      String alias = null;
      while (index < leaves.size() && !COMMA.equals(SyntaxWalk.text(leaves.get(index)))) {
        if (AS.equals(SyntaxWalk.text(leaves.get(index))) && index + 1 < leaves.size()) {
          alias = SyntaxWalk.text(leaves.get(index + 1));
          index++;
        }
        index++;
      }
      out.add(new String[] {first, alias});
      index++;
    }
    return out;
  }
}
