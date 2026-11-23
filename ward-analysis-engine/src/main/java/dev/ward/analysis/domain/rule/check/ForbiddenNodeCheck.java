package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.UnitViews;
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
  private static final String FUNCTION = "funcdef";
  private static final String NAME_PLACEHOLDER = "%name%";
  private static final Pattern FIRST_NAME = Pattern.compile("^[\\w.]+");
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
  public Set<String> nodeKinds() {
    return filter.plainKinds();
  }

  @Override
  public List<RuleMatch> matchesIn(
      final SyntaxNode root,
      final UnitViews views,
      final SourceUnit unit
  ) {
    return report(filter.select(root, SourceFile.of(unit), views.nodes()));
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return report(filter.select(root, file));
  }

  private List<RuleMatch> report(final List<SyntaxWalk.Visit> visits) {
    return visits.stream()
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
   * @param notPath an ancestor path, in the syntax of {@code path}, that the node must not follow;
   *     empty does not limit
   * @param notRootText a pattern that the text of the whole file must not match, if any
   * @param notParentText a pattern that the text of the parent must not match, if any
   * @param notScopeText a pattern that the text of the nearest enclosing function, or of the file,
   *     must not match, if not empty; {@code %name%} stands for the dotted name that starts the node's text
   * @param scopeText a pattern that the text of that scope must match, if not empty, with the same
   *     placeholder
   * @param ancestorKinds the kinds of which the nearest ancestor is the one {@code
   *     notAncestorText} reads
   * @param notAncestorText a pattern that the text of the nearest ancestor of one of {@code
   *     ancestorKinds} must not match, if any
   * @param position the conditions on the node's place among its siblings and ancestors
   */
  public record Condition(
      Optional<Pattern> text,
      Set<String> withChild,
      Set<String> parent,
      String path,
      Optional<Pattern> parentText,
      String notPath,
      Optional<Pattern> notRootText,
      Optional<Pattern> notParentText,
      String notScopeText,
      String scopeText,
      Set<String> ancestorKinds,
      Optional<Pattern> notAncestorText,
      Position position
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
      this(
          text,
          withChild,
          parent,
          path,
          Optional.empty(),
          "",
          Optional.empty(),
          Optional.empty(),
          "",
          "",
          Set.of(),
          Optional.empty(),
          Position.ANYWHERE
      );
    }

    /** Creates a condition without a path the node must not follow. */
    public Condition(
        final Optional<Pattern> text,
        final Set<String> withChild,
        final Set<String> parent,
        final String path,
        final Optional<Pattern> parentText
    ) {
      this(
          text,
          withChild,
          parent,
          path,
          parentText,
          "",
          Optional.empty(),
          Optional.empty(),
          "",
          "",
          Set.of(),
          Optional.empty(),
          Position.ANYWHERE
      );
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
      Objects.requireNonNull(notPath, "notPath");
      Objects.requireNonNull(notRootText, "notRootText");
      Objects.requireNonNull(notParentText, "notParentText");
      Objects.requireNonNull(notScopeText, "notScopeText");
      Objects.requireNonNull(scopeText, "scopeText");
      Objects.requireNonNull(notAncestorText, "notAncestorText");
      ancestorKinds = Set.copyOf(ancestorKinds);
      Objects.requireNonNull(position, "position");
      withChild = Set.copyOf(withChild);
      parent = Set.copyOf(parent);
    }

    boolean holds(final SyntaxWalk.Visit visit) {
      // The text of a node is costly to build, so the conditions on its kind come first.
      return childOk(visit.node())
          && parentOk(visit)
          && pathOk(visit)
          && notPathOk(visit)
          && parentTextOk(visit)
          && rootTextOk(visit)
          && notParentTextOk(visit)
          && enclosingTextOk(visit)
          && textOk(visit.node())
          && position.holds(visit);
    }

    private boolean notPathOk(final SyntaxWalk.Visit visit) {
      return notPath.isEmpty() || !follows(notPath, visit);
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

    private boolean notParentTextOk(final SyntaxWalk.Visit visit) {
      final List<SyntaxNode> up = visit.ancestors();
      return notParentText.isEmpty()
          || up.isEmpty()
          || !notParentText.get().matcher(SyntaxWalk.text(up.get(up.size() - 1))).find();
    }

    private boolean enclosingTextOk(final SyntaxWalk.Visit visit) {
      return scopeTextOk(visit) && notAncestorTextOk(visit);
    }

    private boolean notAncestorTextOk(final SyntaxWalk.Visit visit) {
      return notAncestorText.isEmpty()
          || visit.ancestors().reversed().stream()
              .filter(ancestor -> ancestorKinds.contains(ancestor.kind()))
              .findFirst()
              .map(ancestor -> !notAncestorText.get().matcher(SyntaxWalk.text(ancestor)).find())
              .orElse(true);
    }

    private boolean scopeTextOk(final SyntaxWalk.Visit visit) {
      return (notScopeText.isEmpty() || !scopeMatches(visit, notScopeText))
          && (scopeText.isEmpty() || scopeMatches(visit, scopeText));
    }

    /** Whether the scope of the node, a function or the file, has text that fits {@code regex}. */
    private static boolean scopeMatches(final SyntaxWalk.Visit visit, final String regex) {
      final List<SyntaxNode> up = visit.ancestors();
      final Matcher first = FIRST_NAME.matcher(SyntaxWalk.text(visit.node()));
      if (up.isEmpty() || !first.find()) {
        return false;
      }
      final SyntaxNode scope =
          up.reversed().stream()
              .filter(ancestor -> FUNCTION.equals(ancestor.kind()))
              .findFirst()
              .orElse(up.get(0));
      return Pattern.compile(regex.replace(NAME_PLACEHOLDER, Pattern.quote(first.group())))
          .matcher(SyntaxWalk.text(scope))
          .find();
    }

    private boolean rootTextOk(final SyntaxWalk.Visit visit) {
      final List<SyntaxNode> up = visit.ancestors();
      return notRootText.isEmpty()
          || up.isEmpty()
          || !notRootText.get().matcher(SyntaxWalk.text(up.get(0))).find();
    }

    private boolean pathOk(final SyntaxWalk.Visit visit) {
      return path.isEmpty() || follows(path, visit);
    }

    private static boolean follows(final String ancestors, final SyntaxWalk.Visit visit) {
      final String[] steps = ancestors.split(">");
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

  /**
   * Conditions on where a node stands; an empty part does not limit.
   *
   * @param spacedText a pattern that must be found in the node's text when its tokens are joined by
   *     one space, so that it can tell {@code use a as b} from {@code use aasb}
   * @param lastIn the kind of the nearest ancestor or the node itself that must be the last child of
   *     its parent, so that nothing follows it
   * @param notPreviousText a pattern that the text of the sibling before the nearest ancestor-or-self
   *     of kind {@code previousOf} must not match
   * @param previousOf the kind whose previous sibling {@code notPreviousText} is tested on
   */
  public record Position(
      Optional<Pattern> spacedText,
      String lastIn,
      Optional<Pattern> notPreviousText,
      String previousOf
  ) {
    /** The position that every node meets. */
    public static final Position ANYWHERE =
        new Position(Optional.empty(), "", Optional.empty(), "");

    /**
     * Checks that no part is missing.
     *
     * @throws NullPointerException if a field is missing
     */
    public Position {
      Objects.requireNonNull(spacedText, "spacedText");
      Objects.requireNonNull(lastIn, "lastIn");
      Objects.requireNonNull(notPreviousText, "notPreviousText");
      Objects.requireNonNull(previousOf, "previousOf");
    }

    boolean holds(final SyntaxWalk.Visit visit) {
      return spacedOk(visit.node())
          && (lastIn.isEmpty() || isLast(visit))
          && (previousOf.isEmpty() || previousOk(visit));
    }

    private boolean spacedOk(final SyntaxNode node) {
      return spacedText.map(p -> p.matcher(SyntaxWalk.spacedText(node)).find()).orElse(true);
    }

    private boolean isLast(final SyntaxWalk.Visit visit) {
      final List<SyntaxNode> chain = chain(visit);
      final int at = indexOf(chain, lastIn);
      return at > 0 && lastChild(chain.get(at - 1)) == chain.get(at);
    }

    private boolean previousOk(final SyntaxWalk.Visit visit) {
      final List<SyntaxNode> chain = chain(visit);
      final int at = indexOf(chain, previousOf);
      if (at <= 0) {
        return true;
      }
      final List<SyntaxNode> siblings = chain.get(at - 1).children();
      final int own = siblings.indexOf(chain.get(at));
      return own <= 0
          || notPreviousText.isEmpty()
          || !notPreviousText.get().matcher(SyntaxWalk.text(siblings.get(own - 1))).find();
    }

    private static List<SyntaxNode> chain(final SyntaxWalk.Visit visit) {
      final List<SyntaxNode> chain = new ArrayList<>(visit.ancestors());
      chain.add(visit.node());
      return chain;
    }

    private static int indexOf(final List<SyntaxNode> chain, final String kind) {
      for (int i = chain.size() - 1; i >= 0; i--) {
        if (kind.equals(chain.get(i).kind())) {
          return i;
        }
      }
      return -1;
    }

    private static SyntaxNode lastChild(final SyntaxNode parent) {
      return parent.children().get(parent.children().size() - 1);
    }
  }
}
