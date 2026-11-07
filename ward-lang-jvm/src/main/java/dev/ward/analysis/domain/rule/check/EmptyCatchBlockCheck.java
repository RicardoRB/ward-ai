package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports the catch blocks with no statement. A catch block whose first comment matches {@code
 * commentFormat} is not empty, and a catch whose exception variable matches {@code
 * exceptionVariableName} is exempt. Comments are not in the tree, so the check reads the source
 * text between the braces.
 */
public final class EmptyCatchBlockCheck implements UnitCheck {

  private static final String CATCH_CLAUSE = "catchClause";
  private static final String BLOCK = "block";
  private static final String IDENTIFIER = "identifier";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String LINE_COMMENT = "//";
  private static final String BLOCK_COMMENT = "/*";
  private static final String BLOCK_COMMENT_END = "*/";

  private final Pattern commentFormat;
  private final Pattern exceptionVariableName;
  private final String message;

  /**
   * Creates the check.
   *
   * @param commentFormat the pattern the first comment of an empty catch block must match
   * @param exceptionVariableName the pattern of exception variables that are exempt
   * @param message the message
   */
  public EmptyCatchBlockCheck(
      final Pattern commentFormat, final Pattern exceptionVariableName, final String message) {
    this.commentFormat = Objects.requireNonNull(commentFormat, "commentFormat");
    this.exceptionVariableName = Objects.requireNonNull(exceptionVariableName, "exception");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CATCH_CLAUSE))) {
      final SyntaxNode clause = visit.node();
      if (isEmpty(clause, file) && !isExempt(clause)) {
        matches.add(CheckSupport.at(clause.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean isEmpty(final SyntaxNode clause, final SourceFile file) {
    final SyntaxNode block = clause.children().get(clause.children().size() - 1);
    if (!BLOCK.equals(block.kind())
        || block.children().stream().anyMatch(child -> BLOCK_STATEMENT.equals(child.kind()))) {
      return false;
    }
    final String inner = innerText(block, file.text());
    final String comment = firstComment(inner);
    return comment == null || !commentFormat.matcher(comment).find();
  }

  private boolean isExempt(final SyntaxNode clause) {
    return clause.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .findFirst()
        .map(name -> exceptionVariableName.matcher(SyntaxWalk.text(name)).find())
        .orElse(false);
  }

  private static String innerText(final SyntaxNode block, final String text) {
    final int start = block.span().startOffset() + 1;
    final int end = block.span().endOffset() - 1;
    return start <= end && end <= text.length() ? text.substring(start, end) : "";
  }

  /** The text of the first comment in {@code inner}, or {@code null} when there is none. */
  private static String firstComment(final String inner) {
    final int line = inner.indexOf(LINE_COMMENT);
    final int block = inner.indexOf(BLOCK_COMMENT);
    if (line < 0 && block < 0) {
      return null;
    }
    if (block < 0 || line >= 0 && line < block) {
      final int stop = inner.indexOf('\n', line);
      return inner.substring(line + LINE_COMMENT.length(), stop < 0 ? inner.length() : stop).trim();
    }
    final int stop = inner.indexOf(BLOCK_COMMENT_END, block);
    return inner.substring(block + BLOCK_COMMENT.length(), stop < 0 ? inner.length() : stop).trim();
  }
}
