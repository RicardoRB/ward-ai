package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports the SwiftLint commands of a file that disable a rule for the rest of the file: a blanket
 * {@code disable} that is never re-enabled, a blanket {@code disable} of a rule already disabled,
 * and an {@code enable} of a rule that is not disabled, like SwiftLint's {@code
 * blanket_disable_command}. Only the rule identifiers are reported, at their own columns.
 * Simplification: the {@code allowed_rules} and {@code always_blanket_disable} options are not
 * read.
 */
public final class SwiftBlanketDisableCommandCheck implements RuleCheck {

  private static final String COMMENT = "comment";
  private static final Pattern COMMAND =
      Pattern.compile("^//\\s*swiftlint:(disable|enable)(:(this|next|previous))?\\s+(.*)$");
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");
  private static final String DISABLE = "disable";
  private static final int ACTION_GROUP = 1;
  private static final int MODIFIER_GROUP = 3;
  private static final int RULES_GROUP = 4;
  private static final String ENABLE = "enable";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftBlanketDisableCommandCheck(final String message) {
    this.message = message;
  }

  /** A rule identifier of a command, with the position where it is written. */
  private record Written(String rule, Span span) {}

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<SyntaxNode> comments = new ArrayList<>(SwiftTrees.descendants(root, COMMENT));
    comments.sort(Comparator.comparingInt(comment -> comment.span().startOffset()));
    final Map<String, Written> disabled = new HashMap<>();
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode comment : comments) {
      apply(comment, disabled, matches);
    }
    disabled.values().forEach(written -> matches.add(new RuleMatch(written.span(), message)));
    return List.copyOf(matches);
  }

  private void apply(
      final SyntaxNode comment,
      final Map<String, Written> disabled,
      final List<RuleMatch> matches) {
    final String text = comment.fields().get("text");
    final Matcher command = COMMAND.matcher(text);
    if (!command.matches() || command.group(MODIFIER_GROUP) != null) {
      return;
    }
    final String action = command.group(ACTION_GROUP);
    for (final Written written : identifiers(comment, text, command.group(RULES_GROUP))) {
      if (DISABLE.equals(action)) {
        disable(written, disabled, matches);
      } else if (ENABLE.equals(action)) {
        enable(written, disabled, matches);
      }
    }
  }

  private void disable(
      final Written written, final Map<String, Written> disabled, final List<RuleMatch> matches) {
    if (disabled.containsKey(written.rule())) {
      matches.add(new RuleMatch(written.span(), message));
    }
    disabled.put(written.rule(), written);
  }

  private void enable(
      final Written written, final Map<String, Written> disabled, final List<RuleMatch> matches) {
    if (disabled.remove(written.rule()) == null) {
      matches.add(new RuleMatch(written.span(), message));
    }
  }

  /** The identifiers of a command; {@code rules} is the end of {@code text}. */
  private static List<Written> identifiers(
      final SyntaxNode comment, final String text, final String rules) {
    final List<Written> out = new ArrayList<>();
    final Matcher identifier = IDENTIFIER.matcher(rules);
    final int rulesStart = text.length() - rules.length();
    final Span start = comment.span();
    while (identifier.find()) {
      out.add(written(start, rulesStart + identifier.start(), identifier.group()));
    }
    return out;
  }

  /** The identifier at {@code index} characters into the comment, with its position. */
  private static Written written(final Span start, final int index, final String rule) {
    final Span span =
        new Span(
            start.startLine(),
            start.startColumn() + index,
            start.startLine(),
            start.startColumn() + index,
            start.startOffset() + index,
            start.startOffset() + index);
    return new Written(rule, span);
  }
}
