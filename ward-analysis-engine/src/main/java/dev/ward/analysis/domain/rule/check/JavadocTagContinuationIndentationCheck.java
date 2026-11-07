package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reports each continuation line of a block tag that is indented by fewer spaces after its leading
 * asterisk than the offset.
 */
public final class JavadocTagContinuationIndentationCheck implements RuleCheck {

  private final int offset;
  private final String message;

  /**
   * Creates the check.
   *
   * @param offset the spaces a continuation line must be indented by
   * @param message the message; {@code {max}} is the offset
   */
  public JavadocTagContinuationIndentationCheck(final int offset, final String message) {
    this.offset = offset;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (JavadocTags.isJavadoc(node)) {
        JavadocTags.tags(node).stream()
            .flatMap(tag -> tag.continuation().stream())
            .filter(line -> line.indent() < offset)
            .forEach(
                line ->
                    matches.add(
                        CheckSupport.at(
                            line.span(), CheckSupport.fill(message, "", String.valueOf(offset)))));
      }
    }
    return List.copyOf(matches);
  }
}
