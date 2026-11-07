package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reports each import whose module name appears nowhere else in the file, like Checkstyle's {@code
 * UnusedImports}. The file does not say which symbols a module exports, so a module counts as used
 * when its name is part of any token outside the imports and comments, such as a qualified name or
 * a type named after the module.
 */
public final class UnusedImportCheck implements RuleCheck {

  private static final String IMPORT = "import_declaration";
  private static final String SEPARATOR = ".";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} is the module name
   */
  public UnusedImportCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final StringBuilder code = new StringBuilder();
    collect(root, code);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(IMPORT))) {
      final String module = module(ImportSortCheck.moduleName(SyntaxWalk.text(visit.node())));
      if (!code.toString().contains(module)) {
        matches.add(new RuleMatch(visit.node().span(), message.replace("{value}", module)));
      }
    }
    return List.copyOf(matches);
  }

  private static String module(final String path) {
    final int dot = path.indexOf(SEPARATOR);
    return dot < 0 ? path : path.substring(0, dot);
  }

  private static void collect(final SyntaxNode node, final StringBuilder out) {
    if (IMPORT.equals(node.kind()) || SyntaxWalk.COMMENT_KIND.equals(node.kind())) {
      return;
    }
    final String text = node.fields().get("text");
    if (node.children().isEmpty() && text != null) {
      out.append(text).append(' ');
    }
    node.children().forEach(child -> collect(child, out));
  }
}
