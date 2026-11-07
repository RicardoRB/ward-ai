package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the order of the imports: the groups come in their listed order, each import sits in the
 * group it best matches, imports inside a group are sorted, and groups are separated by a blank
 * line. A group is a name prefix, or a regular expression between slashes. Static imports are
 * ordered with the others.
 */
public final class ImportOrderCheck implements RuleCheck {

  private static final String REGEX_DELIMITER = "/";

  private final List<String> groups;
  private final boolean ordered;
  private final boolean separated;
  private final boolean caseSensitive;
  private final String message;

  /**
   * Creates the check.
   *
   * @param groups the groups, first to last; empty for one group
   * @param ordered whether the imports of a group are sorted
   * @param separated whether groups are separated by a blank line
   * @param caseSensitive whether sorting is case-sensitive
   * @param message the message; {@code {value}} is the imported name
   * @throws IllegalArgumentException if a regular expression group is invalid
   */
  public ImportOrderCheck(
      final List<String> groups,
      final boolean ordered,
      final boolean separated,
      final boolean caseSensitive,
      final String message) {
    this.groups = List.copyOf(groups);
    this.ordered = ordered;
    this.separated = separated;
    this.caseSensitive = caseSensitive;
    this.message = Objects.requireNonNull(message, "message");
    this.groups.forEach(ImportOrderCheck::validate);
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return check(root, List.of());
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return check(root, ImportLines.of(unit));
  }

  private List<RuleMatch> check(final SyntaxNode root, final List<String> lines) {
    final List<RuleMatch> matches = new ArrayList<>();
    ImportDeclaration previous = null;
    int previousGroup = -1;
    for (final ImportDeclaration declaration : ImportDeclaration.declarations(root)) {
      final int group = groupOf(declaration.name());
      if (previous != null && misplaced(previous, previousGroup, declaration, group, lines)) {
        matches.add(
            CheckSupport.at(
                declaration.span(), CheckSupport.fill(message, declaration.fullName(), "")));
      }
      previous = declaration;
      previousGroup = group;
    }
    return List.copyOf(matches);
  }

  private boolean misplaced(
      final ImportDeclaration previous,
      final int previousGroup,
      final ImportDeclaration current,
      final int group,
      final List<String> lines) {
    if (group != previousGroup) {
      return group < previousGroup || (separated && unseparated(previous, current, lines));
    }
    return ordered && compare(previous.fullName(), current.fullName()) > 0;
  }

  private static boolean unseparated(
      final ImportDeclaration previous, final ImportDeclaration current, final List<String> lines) {
    return !lines.isEmpty()
        && ImportLines.blankBetween(lines, previous.span().endLine(), current.span().startLine())
            == 0;
  }

  private int compare(final String left, final String right) {
    return caseSensitive ? left.compareTo(right) : left.compareToIgnoreCase(right);
  }

  /** Returns the index of the best matching group: the longest prefix, or the earliest match. */
  private int groupOf(final String name) {
    int best = groups.size();
    int bestScore = -1;
    for (int index = 0; index < groups.size(); index++) {
      final int score = score(groups.get(index), name);
      if (score > bestScore) {
        best = index;
        bestScore = score;
      }
    }
    return best;
  }

  /** Returns how well a group matches a name, or -1 for no match. */
  private static int score(final String group, final String name) {
    if (isRegex(group)) {
      final Matcher matcher = regex(group).matcher(name);
      return matcher.lookingAt() ? matcher.end() : -1;
    }
    return name.startsWith(group) ? group.length() : -1;
  }

  private static boolean isRegex(final String group) {
    return group.length() >= 2
        && group.startsWith(REGEX_DELIMITER)
        && group.endsWith(REGEX_DELIMITER);
  }

  private static Pattern regex(final String group) {
    return Pattern.compile(group.substring(1, group.length() - 1));
  }

  private static void validate(final String group) {
    if (isRegex(group)) {
      regex(group);
    }
  }
}
