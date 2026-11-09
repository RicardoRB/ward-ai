package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reports a {@code @ts-<directive>} comment that the options ban, or that lacks a description. */
public final class BanTsCommentCheck implements RuleCheck {

  private static final String COMMENT = "comment";
  private static final int BLOCK_MARKERS = 4;
  private static final String BANNED = "true";
  private static final String NEEDS_DESCRIPTION = "allow-with-description";
  private static final String DIRECTIVE = "{directive}";
  private static final String MINIMUM = "{minimum}";
  private static final Pattern PRAGMA = Pattern.compile("^//\\/?\\s*@ts-(check|nocheck)(.*)$");
  private static final Pattern LINE =
      Pattern.compile("^/*\\s*@ts-(expect-error|ignore)(.*)$", Pattern.DOTALL);
  private static final Pattern BLOCK =
      Pattern.compile("^\\s*(?:/|\\*)*\\s*@ts-(expect-error|ignore)(.*)$");

  private final Map<String, String> options;
  private final int minimumDescriptionLength;
  private final String message;

  /**
   * Creates the check; {@code options} maps each directive to true, false or the description rule.
   */
  public BanTsCommentCheck(
      final Map<String, String> options, final int minimumDescriptionLength, final String message) {
    this.options = Map.copyOf(options);
    this.minimumDescriptionLength = minimumDescriptionLength;
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> out = new ArrayList<>();
    final int firstCodeLine = firstCodeLine(root);
    for (final SyntaxNode comment : SyntaxText.nodesOf(root, COMMENT)) {
      final String text = SyntaxText.of(comment);
      final Matcher match = directiveOf(text, comment, firstCodeLine);
      if (match != null) {
        check(out, comment, match.group(1), match.group(2));
      }
    }
    return out;
  }

  private Matcher directiveOf(
      final String text, final SyntaxNode comment, final int firstCodeLine) {
    if (text.startsWith("//")) {
      final Matcher pragma = PRAGMA.matcher(text);
      if (pragma.matches()) {
        final boolean nocheck = "nocheck".equals(pragma.group(1));
        return nocheck && !isBeforeCode(comment, firstCodeLine) ? null : pragma;
      }
      final Matcher line = LINE.matcher(text.substring(2));
      return line.matches() ? line : null;
    }
    final String body = text.length() >= BLOCK_MARKERS ? text.substring(2, text.length() - 2) : "";
    final String[] lines = body.split("\\R", -1);
    final Matcher block = BLOCK.matcher(lines[lines.length - 1]);
    return block.matches() ? block : null;
  }

  private static boolean isBeforeCode(final SyntaxNode comment, final int firstCodeLine) {
    return firstCodeLine > comment.span().startLine();
  }

  private void check(
      final List<RuleMatch> out, final SyntaxNode comment, final String name, final String text) {
    final String directive = "ts-" + name;
    final String option = options.getOrDefault(directive, "false");
    if (BANNED.equals(option)) {
      out.add(new RuleMatch(comment.span(), fill(directive)));
    } else if (NEEDS_DESCRIPTION.equals(option)
        && text.trim().length() < minimumDescriptionLength) {
      out.add(new RuleMatch(comment.span(), fill(directive)));
    }
  }

  private String fill(final String directive) {
    return message
        .replace(DIRECTIVE, directive.substring("ts-".length()))
        .replace(MINIMUM, Integer.toString(minimumDescriptionLength));
  }

  private static int firstCodeLine(final SyntaxNode root) {
    return SyntaxText.leaves(root).stream()
        .filter(leaf -> !COMMENT.equals(leaf.kind()))
        .findFirst()
        .map(leaf -> leaf.span().startLine())
        .orElse(Integer.MAX_VALUE);
  }
}
