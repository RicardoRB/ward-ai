package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reports a backreference that can never take a value, like ESLint {@code
 * no-useless-backreference}: a reference to a group that comes later in the pattern, to a group it
 * is inside, or to a group in another alternative. The check reads the regular expression literals
 * of the source; it reports each at the start of its literal, as ESLint does.
 */
public final class NodeUselessBackreferenceCheck implements RuleCheck {

  private static final String REGEX = "RegularExpressionLiteral";
  private static final char SLASH = '/';
  private static final char BACKSLASH = '\\';
  private static final char OPEN = '(';
  private static final char CLOSE = ')';
  private static final char BAR = '|';
  private static final char OPEN_CLASS = '[';
  private static final char CLOSE_CLASS = ']';
  private static final char QUESTION = '?';
  private static final char NAME_OPEN = '<';
  private static final int NAME_OFFSET = 3;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding, with {@code {value}} as the backreference
   */
  public NodeUselessBackreferenceCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (REGEX.equals(node.kind())) {
      final String literal = LeafText.of(node);
      final int end = literal.lastIndexOf(SLASH);
      if (end > 0) {
        for (final String reference : uselessReferences(literal.substring(1, end))) {
          matches.add(new RuleMatch(node.span(), message.replace("{value}", reference)));
        }
      }
    }
    node.children().forEach(child -> collect(child, matches));
  }

  /** A capturing group: its number, the index of its parenthesis and of its closing one. */
  private record Group(int number, int open, int close, List<String> path) {}

  /** A backreference: its number, its text, its index and the path of enclosing alternatives. */
  private record Reference(int number, String text, int index, List<String> path) {}

  /** The backreferences of a pattern that can never take a value, as their text. */
  static List<String> uselessReferences(final String pattern) {
    final ReferenceScan scan = new ReferenceScan(pattern);
    scan.run();
    final List<String> useless = new ArrayList<>();
    for (final Reference reference : scan.references) {
      final Group group = scan.groups.get(reference.number());
      if (group != null && useless(group, reference)) {
        useless.add(reference.text());
      }
    }
    return useless;
  }

  private static boolean useless(final Group group, final Reference reference) {
    return group.open() > reference.index()
        || group.close() > reference.index() && group.open() < reference.index()
        || divergent(group.path(), reference.path());
  }

  /**
   * Whether two paths of alternatives split inside a common group, one reference in another
   * alternative.
   */
  private static boolean divergent(final List<String> group, final List<String> reference) {
    final int common = Math.min(group.size(), reference.size());
    for (int level = 0; level < common; level++) {
      final String[] left = group.get(level).split(":");
      final String[] right = reference.get(level).split(":");
      if (!left[0].equals(right[0])) {
        return false;
      }
      if (!left[1].equals(right[1])) {
        return true;
      }
    }
    return false;
  }

  /** A single pass over a pattern: its groups, and the backreferences with their paths. */
  private static final class ReferenceScan {

    private final String pattern;
    private final Map<Integer, Group> groups = new HashMap<>();
    private final List<Reference> references = new ArrayList<>();
    private final List<Frame> frames = new ArrayList<>();
    private int count;
    private static final int NONE = -1;
    private static final int ROOT_FRAMES = 1;

    /** A parenthesis frame: its index, the alternative currently open, and its group number. */
    private record Frame(int index, int group, int alternative) {}

    ReferenceScan(final String pattern) {
      this.pattern = pattern;
      frames.add(new Frame(NONE, NONE, 0));
    }

    void run() {
      int index = 0;
      while (index < pattern.length()) {
        final char c = pattern.charAt(index);
        if (c == BACKSLASH) {
          index = escape(index);
        } else if (c == OPEN_CLASS) {
          index = skipClass(index);
        } else if (c == OPEN) {
          index = open(index);
        } else if (c == CLOSE) {
          close(index);
          index++;
        } else {
          if (c == BAR) {
            alternate();
          }
          index++;
        }
      }
    }

    private int escape(final int start) {
      int end = start + 1;
      if (end < pattern.length() && pattern.charAt(end) >= '1' && pattern.charAt(end) <= '9') {
        while (end < pattern.length() && Character.isDigit(pattern.charAt(end))) {
          end++;
        }
        references.add(
            new Reference(
                Integer.parseInt(pattern.substring(start + 1, end)),
                pattern.substring(start, end),
                start,
                path()));
        return end;
      }
      return start + 2;
    }

    private int skipClass(final int start) {
      int index = start + 1;
      while (index < pattern.length() && pattern.charAt(index) != CLOSE_CLASS) {
        index += pattern.charAt(index) == BACKSLASH ? 2 : 1;
      }
      return index + 1;
    }

    private int open(final int index) {
      final boolean capturing =
          !(index + 1 < pattern.length() && pattern.charAt(index + 1) == QUESTION)
              || index + 2 < pattern.length()
                  && pattern.charAt(index + 2) == NAME_OPEN
                  && index + NAME_OFFSET < pattern.length()
                  && pattern.charAt(index + NAME_OFFSET) != '='
                  && pattern.charAt(index + NAME_OFFSET) != '!';
      final int group = capturing ? count + 1 : NONE;
      if (capturing) {
        count++;
      }
      if (capturing) {
        groups.put(group, new Group(group, index, NONE, path()));
      }
      frames.add(new Frame(index, group, 0));
      return index + 1;
    }

    private void close(final int index) {
      if (frames.size() <= ROOT_FRAMES) {
        return;
      }
      final Frame frame = frames.remove(frames.size() - 1);
      if (frame.group() != NONE) {
        final Group open = groups.get(frame.group());
        groups.put(frame.group(), new Group(open.number(), open.open(), index, open.path()));
      }
    }

    private void alternate() {
      final Frame top = frames.get(frames.size() - 1);
      frames.set(frames.size() - 1, new Frame(top.index(), top.group(), top.alternative() + 1));
    }

    /** The enclosing frames with their alternatives, outermost first, as identity:alternative. */
    private List<String> path() {
      final List<String> path = new ArrayList<>();
      for (final Frame frame : frames) {
        path.add(frame.index() + ":" + frame.alternative());
      }
      return path;
    }
  }
}
