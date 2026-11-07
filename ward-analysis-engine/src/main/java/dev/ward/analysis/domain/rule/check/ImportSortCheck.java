package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reports each import that comes before the import preceding it in the file, when the names sort as
 * plain text, like Checkstyle's {@code CustomImportOrder} for one group of imports.
 */
public final class ImportSortCheck implements RuleCheck {

  private static final String IMPORT = "import_declaration";
  private static final String KEYWORD = "import";
  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} is the module name
   */
  public ImportSortCheck(final String message) {
    this.message = message;
  }

  /**
   * Returns the module name after the {@code import} keyword, past any attribute such as
   * {@code @testable}.
   */
  static String moduleName(final String text) {
    return text.substring(text.indexOf(KEYWORD) + KEYWORD.length());
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    String previous = null;
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(IMPORT))) {
      final String name = moduleName(SyntaxWalk.text(visit.node()));
      if (previous != null && name.compareTo(previous) < 0) {
        matches.add(new RuleMatch(visit.node().span(), message.replace("{value}", name)));
      }
      previous = name;
    }
    return List.copyOf(matches);
  }
}
