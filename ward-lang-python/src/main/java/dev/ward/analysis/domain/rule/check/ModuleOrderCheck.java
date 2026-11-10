package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports an import or a {@code __future__} import that comes after other module-level statements,
 * like pyflakes {@code F404} and pycodestyle {@code E402}. A leading docstring is not a statement
 * for this purpose, and a {@code __future__} import must come before every other statement.
 */
public final class ModuleOrderCheck implements RuleCheck {

  /** The mistakes the check tells apart, one per rule. */
  public enum Problem {
    /** An import after a statement that is not an import or a docstring. */
    IMPORT_AFTER_CODE,
    /** A {@code __future__} import after any statement but the docstring. */
    FUTURE_AFTER_STATEMENT
  }

  private static final Set<String> IMPORTS = Set.of("import_name", "import_from");

  /** Definitions are code even when their body imports, so their subtrees are not searched. */
  private static final Set<String> DEFINITIONS =
      Set.of("funcdef", "async_funcdef", "classdef", "decorated");

  private static final Pattern FUTURE = Pattern.compile("^from__future__import");
  private static final Pattern DOCSTRING = Pattern.compile("^[rRbBuU]?[\"']");

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public ModuleOrderCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    boolean statement = false;
    boolean code = false;
    for (final SyntaxNode child : root.children()) {
      final String text = SyntaxWalk.text(child);
      if (text.isBlank() || (!statement && DOCSTRING.matcher(text).find())) {
        continue;
      }
      final boolean future = isFuture(child);
      final boolean imported = isImport(child);
      if (report(future, imported, statement, code)) {
        found.add(new RuleMatch(child.span(), message));
      }
      statement = true;
      code = code || !imported;
    }
    return List.copyOf(found);
  }

  /** Whether the statement breaks the order that {@link #problem} checks. */
  private boolean report(
      final boolean future, final boolean imported, final boolean statement, final boolean code) {
    return switch (problem) {
      case FUTURE_AFTER_STATEMENT -> future && statement;
      case IMPORT_AFTER_CODE -> imported && code;
    };
  }

  private static boolean isImport(final SyntaxNode node) {
    return IMPORTS.contains(node.kind())
        || IMPORTS.stream()
            .anyMatch(kind -> SyntaxWalk.firstDescendant(node, kind, DEFINITIONS).isPresent());
  }

  private static boolean isFuture(final SyntaxNode node) {
    return SyntaxWalk.descendants(node).stream()
        .anyMatch(
            child ->
                "import_from".equals(child.kind())
                    && FUTURE.matcher(SyntaxWalk.text(child)).find());
  }
}
