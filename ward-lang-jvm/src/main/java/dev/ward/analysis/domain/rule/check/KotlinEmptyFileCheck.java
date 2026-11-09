package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;

/**
 * Reports a Kotlin file that has no declaration and no file annotation, so that it holds only its
 * package and imports or comments, as ktlint's {@code no-empty-file} rule does.
 */
public final class KotlinEmptyFileCheck implements RuleCheck {

  private static final String FILE = "kotlinFile";
  private static final String TOP_LEVEL = "topLevelObject";
  private static final String PREAMBLE = "preamble";
  private static final String FILE_ANNOTATIONS = "fileAnnotations";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the start of the file
   */
  public KotlinEmptyFileCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    if (!FILE.equals(root.kind()) || hasCode(root)) {
      return List.of();
    }
    return List.of(CheckSupport.at(KotlinLeaves.fileStart(), message));
  }

  private static boolean hasCode(final SyntaxNode root) {
    return root.children().stream()
        .anyMatch(
            child ->
                TOP_LEVEL.equals(child.kind())
                    || (PREAMBLE.equals(child.kind()) && hasFileAnnotations(child)));
  }

  private static boolean hasFileAnnotations(final SyntaxNode preamble) {
    return preamble.children().stream().anyMatch(part -> FILE_ANNOTATIONS.equals(part.kind()));
  }
}
