package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks imports against an ordered list of groups. A group is a list of tokens, and an import
 * belongs to the first group that holds a token matching it: {@code STATIC} for a static import,
 * {@code SPECIAL_IMPORTS}, {@code STANDARD_JAVA_PACKAGE} or {@code THIRD_PARTY_PACKAGE} for a name
 * matching the matching regular expression. Groups are written separated by {@code ###}, and the
 * tokens of one group by {@code ####}. An import outside every group, or out of group order, is
 * reported; so is an unsorted import when sorting is on, and a group change without a blank line
 * when lines are separated.
 */
public final class CustomImportOrderCheck implements RuleCheck {

  private static final String GROUP_SEPARATOR = "(?<!#)###(?!#)";
  private static final String MEMBER_SEPARATOR = "####";
  private static final String STATIC = "STATIC";
  private static final String SPECIAL = "SPECIAL_IMPORTS";
  private static final String STANDARD = "STANDARD_JAVA_PACKAGE";
  private static final String THIRD_PARTY = "THIRD_PARTY_PACKAGE";
  private static final Set<String> TOKENS = Set.of(STATIC, SPECIAL, STANDARD, THIRD_PARTY);

  private final List<List<String>> groups;
  private final Rules rules;
  private final boolean sortAlphabetically;
  private final boolean separateLines;
  private final String message;

  /**
   * The regular expressions of the group tokens.
   *
   * @param order the groups, as written in the rule option
   * @param special the pattern of {@code SPECIAL_IMPORTS}
   * @param standard the pattern of {@code STANDARD_JAVA_PACKAGE}
   * @param thirdParty the pattern of {@code THIRD_PARTY_PACKAGE}
   */
  public record Rules(String order, String special, String standard, String thirdParty) {

    /**
     * Compiles the patterns.
     *
     * @throws NullPointerException if a field is missing
     * @throws IllegalArgumentException if a pattern or token is invalid
     */
    public Rules {
      Objects.requireNonNull(order, "order");
      Objects.requireNonNull(special, "special");
      Objects.requireNonNull(standard, "standard");
      Objects.requireNonNull(thirdParty, "thirdParty");
      Pattern.compile(special);
      Pattern.compile(standard);
      Pattern.compile(thirdParty);
    }
  }

  /**
   * Creates the check.
   *
   * @param rules the group order and the patterns
   * @param sortAlphabetically whether the imports of a group are sorted
   * @param separateLines whether a blank line must separate groups
   * @param message the message; {@code {value}} is the imported name
   * @throws IllegalArgumentException if the order names an unknown token
   */
  public CustomImportOrderCheck(
      final Rules rules,
      final boolean sortAlphabetically,
      final boolean separateLines,
      final String message) {
    this.rules = Objects.requireNonNull(rules, "rules");
    this.groups = parse(rules.order());
    this.sortAlphabetically = sortAlphabetically;
    this.separateLines = separateLines;
    this.message = Objects.requireNonNull(message, "message");
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
      final int group = groupOf(declaration);
      if (group < 0) {
        matches.add(at(declaration));
        continue;
      }
      if (previous != null && misplaced(previous, previousGroup, declaration, group, lines)) {
        matches.add(at(declaration));
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
    final int blank =
        lines.isEmpty()
            ? -1
            : ImportLines.blankBetween(
                lines, previous.span().endLine(), current.span().startLine());
    if (group != previousGroup) {
      return group < previousGroup || (separateLines && blank == 0);
    }
    return (separateLines && blank > 0)
        || (sortAlphabetically && previous.fullName().compareTo(current.fullName()) > 0);
  }

  private RuleMatch at(final ImportDeclaration declaration) {
    return CheckSupport.at(
        declaration.span(), CheckSupport.fill(message, declaration.fullName(), ""));
  }

  /** Returns the index of the first group holding a matching token, or -1 for none. */
  private int groupOf(final ImportDeclaration declaration) {
    for (int group = 0; group < groups.size(); group++) {
      for (final String token : groups.get(group)) {
        if (matches(token, declaration)) {
          return group;
        }
      }
    }
    return -1;
  }

  private boolean matches(final String token, final ImportDeclaration declaration) {
    final String name = declaration.name();
    return switch (token) {
      case STATIC -> declaration.isStatic();
      case SPECIAL -> find(rules.special(), name);
      case STANDARD -> find(rules.standard(), name);
      case THIRD_PARTY -> find(rules.thirdParty(), name);
      default -> false;
    };
  }

  private static boolean find(final String regex, final String name) {
    return Pattern.compile(regex).matcher(name).find();
  }

  private static List<List<String>> parse(final String order) {
    if (order.isBlank()) {
      return List.of();
    }
    final List<List<String>> groups = new ArrayList<>();
    for (final String group : order.split(GROUP_SEPARATOR)) {
      final List<String> tokens = new ArrayList<>();
      for (final String token : group.split(MEMBER_SEPARATOR)) {
        tokens.add(token(token.trim()));
      }
      groups.add(List.copyOf(tokens));
    }
    return List.copyOf(groups);
  }

  private static String token(final String token) {
    if (!TOKENS.contains(token)) {
      throw new IllegalArgumentException("unknown import group token '" + token + "'");
    }
    return token;
  }
}
