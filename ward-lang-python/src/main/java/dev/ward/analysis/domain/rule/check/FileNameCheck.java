package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reports a module whose file name has a non-ASCII character, like pylint {@code
 * non-ascii-file-name}. The name is the file name without its directory and extension.
 */
public final class FileNameCheck implements RuleCheck {

  private static final Pattern NON_ASCII = Pattern.compile("[^\\x00-\\x7F]");
  private static final String EXTENSION = ".py";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public FileNameCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    final String name = fileName(unit.path());
    if (!NON_ASCII.matcher(name).find()) {
      return List.of();
    }
    return List.of(new RuleMatch(root.span(), message));
  }

  private static String fileName(final String path) {
    final String base = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
    return base.endsWith(EXTENSION) ? base.substring(0, base.length() - EXTENSION.length()) : base;
  }
}
