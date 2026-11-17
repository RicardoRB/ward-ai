package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports each node of the filter's kinds, limited by the {@link Condition} when it has any part.
 * The user options are {@code text} and the filters' {@code kinds}.
 */
public final class ForbiddenNodeCheck implements UnitCheck {
  private static final Set<String> KEYWORDS =
      Set.of(
          "assert",
          "new",
          "if",
          "for",
          "while",
          "do",
          "return",
          "throw",
          "try",
          "switch",
          "synchronized",
          "catch",
          "else",
          "super"
      );
  private static final char ESCAPE = '\\';
  private static final Pattern LEADING_KEYWORD = Pattern.compile("^\\^([a-z]+)(?![a-zA-Z])");

  private final NodeFilter filter;
  private final Condition condition;
  private final String message;

  /** Creates the check; {@code {value}} in the message is the node's text. */
  public ForbiddenNodeCheck(
      final NodeFilter filter,
      final Condition condition,
      final String message
  ) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.condition = Objects.requireNonNull(condition, "condition");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return filter.select(root, file).stream()
        .filter(condition::holds)
        .map(
            v ->
                CheckSupport.at(
                    v.node().span(),
                    CheckSupport.fill(message, SyntaxWalk.text(v.node()), "")
                )
        )
        .toList();
  }

  /**
   * What else a node must have to be reported; an empty part does not limit.
   *
   * @param text a pattern that must be found in the node's text, if any
   * @param withChild kinds of which a direct child must exist, if not empty
   * @param parent kinds of which the parent must be, if not empty
   * @param path the kinds of the ancestors, nearest first, as {@code parent>grandparent}; each step
   *     may list alternatives as {@code a|b}; empty does not limit
   * @param parentText a pattern that must be found in the parent's text, if any
   */
  public record Condition(
      Optional<Pattern> text,
      Set<String> withChild,
      Set<String> parent,
      String path,
      Optional<Pattern> parentText
  ) {
    /** The condition that every node meets. */
    public static final Condition ANY = new Condition(Optional.empty(), Set.of(), Set.of());

    /** Creates a condition without an ancestor path. */
    public Condition(
        final Optional<Pattern> text,
        final Set<String> withChild,
        final Set<String> parent
    ) {
      this(text, withChild, parent, "");
    }

    /** Creates a condition without a pattern on the parent. */
    public Condition(
        final Optional<Pattern> text,
        final Set<String> withChild,
        final Set<String> parent,
        final String path
    ) {
      this(text, withChild, parent, path, Optional.empty());
    }

    /**
     * Copies the sets.
     *
     * @throws NullPointerException if a field is missing
     */
    public Condition {
      Objects.requireNonNull(text, "text");
      Objects.requireNonNull(path, "path");
      Objects.requireNonNull(parentText, "parentText");
      withChild = Set.copyOf(withChild);
      parent = Set.copyOf(parent);
    }

    boolean holds(final SyntaxWalk.Visit visit) {
      // The text of a node is costly to build, so the conditions on its kind come first.
      return childOk(visit.node())
          && parentOk(visit)
          && pathOk(visit)
          && parentTextOk(visit)
          && textOk(visit.node());
    }

    private boolean textOk(final SyntaxNode node) {
      return text.map(p -> keywordStarts(p, node) && p.matcher(SyntaxWalk.text(node)).find())
          .orElse(true);
    }

    private boolean childOk(final SyntaxNode node) {
      return withChild.isEmpty()
          || node.children().stream().anyMatch(c -> withChild.contains(c.kind()));
    }

    private static boolean keywordStarts(final Pattern pattern, final SyntaxNode node) {
      final List<String> keywords = new ArrayList<>();
      for (final String alternative : alternatives(pattern.pattern())) {
        final Matcher keyword = LEADING_KEYWORD.matcher(alternative);
        if (!keyword.find() || !KEYWORDS.contains(keyword.group(1))) {
          return true;
        }
        keywords.add(keyword.group(1));
      }
      return SyntaxWalk.firstLeaf(node)
          .map(first -> keywords.contains(String.valueOf(first.fields().get("text"))))
          .orElse(true);
    }

    /** Splits a regular expression at its top-level {@code |}. */
    private static List<String> alternatives(final String regex) {
      final List<String> parts = new ArrayList<>();
      int depth = 0;
      int start = 0;
      int index = 0;
      while (index < regex.length()) {
        final char c = regex.charAt(index);
        if (c == ESCAPE) {
          index++;
        } else if (c == '(' || c == '[') {
          depth++;
        } else if (c == ')' || c == ']') {
          depth--;
        } else if (c == '|' && depth == 0) {
          parts.add(regex.substring(start, index));
          start = index + 1;
        }
        index++;
      }
      parts.add(regex.substring(start));
      return parts;
    }

    private boolean parentOk(final SyntaxWalk.Visit visit) {
      if (parent.isEmpty()) {
        return true;
      }
      final List<SyntaxNode> up = visit.ancestors();
      return !up.isEmpty() && parent.contains(up.get(up.size() - 1).kind());
    }

    private boolean parentTextOk(final SyntaxWalk.Visit visit) {
      final List<SyntaxNode> up = visit.ancestors();
      return parentText.isEmpty()
          || !up.isEmpty()
          && parentText.get().matcher(SyntaxWalk.text(up.get(up.size() - 1))).find();
    }

    private boolean pathOk(final SyntaxWalk.Visit visit) {
      if (path.isEmpty()) {
        return true;
      }
      final String[] steps = path.split(">");
      final List<SyntaxNode> up = visit.ancestors();
      if (up.size() < steps.length) {
        return false;
      }
      for (int step = 0; step < steps.length; step++) {
        final String kind = up.get(up.size() - 1 - step).kind();
        if (!Set.of(steps[step].split("\\|")).contains(kind)) {
          return false;
        }
      }
      return true;
    }
  }
}
