package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Matches a regular expression against lines, comments, string literals, the file name or the file
 * header. A forbidding rule reports each matching item beyond {@code limit}; a requiring rule
 * reports the file once when fewer than {@code limit} items match. The user options are {@code
 * pattern}, {@code limit} and {@code header-lines}.
 */
public final class TextMatchCheck implements UnitCheck {

  private static final Span FILE_START = new Span(1, 1, 1, 1, 0, 0);

  private final NodeFilter filter;
  private final Rule rule;
  private final String message;

  /** Creates the check; for comments and strings the filter's kinds name the nodes to read. */
  public TextMatchCheck(final NodeFilter filter, final Rule rule, final String message) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.rule = Objects.requireNonNull(rule, "rule");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    if (!filter.sources().admits(file.path())) {
      return List.of();
    }
    final List<Item> hits = items(root, file).stream().filter(this::matches).toList();
    if (rule.required()) {
      return hits.size() >= rule.limit()
          ? List.of()
          : List.of(CheckSupport.at(FILE_START, message));
    }
    final List<RuleMatch> matches = new ArrayList<>();
    hits.stream().skip(rule.limit()).forEach(hit -> matches.add(report(hit)));
    return List.copyOf(matches);
  }

  private RuleMatch report(final Item hit) {
    return CheckSupport.at(
        hit.span(), CheckSupport.fill(message, hit.text().strip(), String.valueOf(rule.limit())));
  }

  private boolean matches(final Item item) {
    return rule.pattern().matcher(item.text()).find();
  }

  private List<Item> items(final SyntaxNode root, final SourceFile file) {
    final Target target = rule.target();
    if (target == Target.LINES) {
      return lines(file);
    }
    if (target == Target.FILENAME) {
      return List.of(new Item(file.name(), FILE_START));
    }
    if (target == Target.HEADER) {
      return header(file);
    }
    final boolean defaultComments = target == Target.COMMENTS && filter.kinds().isEmpty();
    return nodes(root, defaultComments ? Set.of(SyntaxWalk.COMMENT_KIND) : filter.kinds());
  }

  private static List<Item> nodes(final SyntaxNode root, final Set<String> kinds) {
    return SyntaxWalk.visits(root, kinds).stream()
        .map(visit -> new Item(textOf(visit.node()), visit.node().span()))
        .toList();
  }

  private static String textOf(final SyntaxNode node) {
    final String own = node.fields().get("text");
    return own != null ? own : SyntaxWalk.text(node);
  }

  private List<Item> header(final SourceFile file) {
    final List<String> lines = file.lines();
    final int count = Math.min(rule.headerLines(), lines.size());
    return List.of(new Item(String.join("\n", lines.subList(0, count)), FILE_START));
  }

  private static List<Item> lines(final SourceFile file) {
    final List<Item> items = new ArrayList<>();
    int offset = 0;
    int number = 1;
    for (final String line : file.lines()) {
      items.add(lineItem(line, number, offset));
      offset += line.length() + 1;
      number++;
    }
    return items;
  }

  private static Item lineItem(final String line, final int number, final int offset) {
    return new Item(
        line, new Span(number, 1, number, line.length() + 1, offset, offset + line.length()));
  }

  private record Item(String text, Span span) {}

  /** What the pattern is matched against. */
  public enum Target {
    /** Each line of the file. */
    LINES,
    /** Each comment node. */
    COMMENTS,
    /** Each node of the filter's kinds, such as string literals. */
    STRINGS,
    /** The last segment of the path. */
    FILENAME,
    /** The first lines of the file. */
    HEADER
  }

  /**
   * The text rule.
   *
   * @param target what to match
   * @param pattern the regular expression, searched for anywhere in the item
   * @param required whether the pattern must match, instead of being forbidden
   * @param limit with {@code required} the fewest matching items; otherwise the most allowed
   * @param headerLines how many lines the header has, for {@link Target#HEADER}
   */
  public record Rule(Target target, Pattern pattern, boolean required, int limit, int headerLines) {

    /**
     * Validates the fields.
     *
     * @throws IllegalArgumentException if the limit or header size is negative
     */
    public Rule {
      Objects.requireNonNull(target, "target");
      Objects.requireNonNull(pattern, "pattern");
      CheckSupport.nonNegative(limit, "limit");
      CheckSupport.nonNegative(headerLines, "header-lines");
    }
  }
}
