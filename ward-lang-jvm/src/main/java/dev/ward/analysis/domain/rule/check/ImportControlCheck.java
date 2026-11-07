package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Checks each import against an ordered list of allow and disallow rules. The first rule that
 * matches decides; an import that no rule matches is not allowed. A pattern is either an exact
 * name, or a package with a trailing {@code .*} that admits every name below it. The check runs
 * only on files whose path matches {@code path}, and does nothing when there are no rules.
 */
public final class ImportControlCheck implements RuleCheck {

  private static final String ALLOW = "allow";
  private static final String DISALLOW = "disallow";
  private static final String STAR_SUFFIX = ".*";

  private final List<Rule> rules;
  private final Pattern path;
  private final String message;

  /**
   * One rule: {@code allow <pattern>} or {@code disallow <pattern>}.
   *
   * @param allowed whether the rule allows the matching imports
   * @param pattern the exact name, or a package ending in {@code .*}
   */
  public record Rule(boolean allowed, String pattern) {

    /**
     * Checks the pattern.
     *
     * @throws NullPointerException if the pattern is missing
     * @throws IllegalArgumentException if the pattern is blank
     */
    public Rule {
      Objects.requireNonNull(pattern, "pattern");
      if (pattern.isBlank()) {
        throw new IllegalArgumentException("an import control rule names no pattern");
      }
    }

    /** Parses {@code allow <pattern>} or {@code disallow <pattern>}. */
    public static Rule parse(final String entry) {
      final String text = entry.trim();
      final int space = text.indexOf(' ');
      final String keyword = space < 0 ? text : text.substring(0, space);
      final String pattern = space < 0 ? "" : text.substring(space + 1).trim();
      return switch (keyword) {
        case ALLOW -> new Rule(true, pattern);
        case DISALLOW -> new Rule(false, pattern);
        default ->
            throw new IllegalArgumentException(
                "an import control rule starts with allow or disallow: '" + entry + "'");
      };
    }

    boolean matches(final String name) {
      return pattern.endsWith(STAR_SUFFIX)
          ? name.startsWith(pattern.substring(0, pattern.length() - 1))
          : name.equals(pattern);
    }
  }

  /**
   * Creates the check.
   *
   * @param rules the rules, as {@code allow <pattern>} or {@code disallow <pattern>}
   * @param path the file paths the check applies to
   * @param message the message; {@code {value}} is the imported name
   * @throws IllegalArgumentException if a rule is malformed
   */
  public ImportControlCheck(final List<String> rules, final Pattern path, final String message) {
    this.rules = rules.stream().map(Rule::parse).toList();
    this.path = Objects.requireNonNull(path, "path");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return check(root, "");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return check(root, unit == null ? "" : unit.path());
  }

  private List<RuleMatch> check(final SyntaxNode root, final String file) {
    final List<RuleMatch> matches = new ArrayList<>();
    if (rules.isEmpty() || !path.matcher(file).find()) {
      return matches;
    }
    for (final ImportDeclaration declaration : ImportDeclaration.declarations(root)) {
      if (!permitted(declaration.fullName())) {
        matches.add(
            CheckSupport.at(
                declaration.span(), CheckSupport.fill(message, declaration.fullName(), "")));
      }
    }
    return List.copyOf(matches);
  }

  private boolean permitted(final String name) {
    for (final Rule rule : rules) {
      if (rule.matches(name)) {
        return rule.allowed();
      }
    }
    return false;
  }
}
