package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks how annotation elements are written. {@code closingParens} asks for or against the empty
 * parentheses of an annotation without elements; {@code elementStyle} asks for named, compact or
 * unwrapped single values; {@code trailingArrayComma} asks for or against a comma before the
 * closing brace of an array. Each option accepts {@code ignore} to skip its check.
 */
public final class AnnotationUseStyleCheck implements RuleCheck {

  private static final String ANNOTATION = "annotation";
  private static final char QUOTE = '"';
  private static final String ARGUMENTS = "annotationFieldValues";
  private static final String ALWAYS = "always";
  private static final String NEVER = "never";
  private static final String IGNORE = "ignore";
  private static final String EXPANDED = "expanded";
  private static final String COMPACT = "compact";
  private static final String COMPACT_NO_ARRAY = "compact-no-array";
  private static final Pattern NAMED =
      Pattern.compile("^\\s*[A-Za-z_][A-Za-z0-9_]*\\s*=.*", Pattern.DOTALL);
  private static final Pattern TRAILING_COMMA = Pattern.compile(",\\s*}");
  private static final Pattern NAMED_VALUE =
      Pattern.compile("^\\s*value\\s*=\\s*(.*)$", Pattern.DOTALL);
  private static final Set<String> CLOSING = Set.of(ALWAYS, NEVER, IGNORE);
  private static final Set<String> ELEMENT = Set.of(COMPACT, COMPACT_NO_ARRAY, EXPANDED, IGNORE);
  private static final Set<String> TRAILING = Set.of(ALWAYS, NEVER, IGNORE);

  private final String closingParens;
  private final String elementStyle;
  private final String trailingArrayComma;
  private final String message;

  /**
   * Creates the check.
   *
   * @param closingParens {@code always}, {@code never} or {@code ignore}
   * @param elementStyle {@code compact}, {@code compact-no-array}, {@code expanded} or {@code
   *     ignore}
   * @param trailingArrayComma {@code always}, {@code never} or {@code ignore}
   * @param message the message
   */
  public AnnotationUseStyleCheck(
      final String closingParens,
      final String elementStyle,
      final String trailingArrayComma,
      final String message) {
    this.closingParens = option(closingParens, CLOSING, "closing-parens");
    this.elementStyle = option(elementStyle, ELEMENT, "element-style");
    this.trailingArrayComma = option(trailingArrayComma, TRAILING, "trailing-array-comma");
    this.message = Objects.requireNonNull(message, "message");
  }

  private static String option(final String value, final Set<String> allowed, final String name) {
    if (!allowed.contains(value)) {
      throw new IllegalArgumentException(name + " cannot be " + value);
    }
    return value;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(ANNOTATION))) {
      final SyntaxNode annotation = visit.node();
      if (isBad(annotation)) {
        matches.add(CheckSupport.at(annotation.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean isBad(final SyntaxNode annotation) {
    final SyntaxNode arguments =
        annotation.children().stream()
            .filter(child -> ARGUMENTS.equals(child.kind()))
            .findFirst()
            .orElse(null);
    final String inner = arguments == null ? null : SyntaxWalk.text(arguments);
    return badParens(inner) || badElements(inner) || badTrailingComma(inner);
  }

  private boolean badParens(final String inner) {
    if (ALWAYS.equals(closingParens)) {
      return inner == null;
    }
    return NEVER.equals(closingParens) && "()".equals(inner);
  }

  private boolean badElements(final String inner) {
    if (inner == null || IGNORE.equals(elementStyle) || "()".equals(inner)) {
      return false;
    }
    final List<String> elements = elements(inner.substring(1, inner.length() - 1));
    if (EXPANDED.equals(elementStyle)) {
      return elements.stream().anyMatch(element -> !NAMED.matcher(element).matches());
    }
    return elements.size() == 1 && badSingle(elements.get(0));
  }

  private boolean badSingle(final String element) {
    return NAMED_VALUE.matcher(element).matches()
        || COMPACT_NO_ARRAY.equals(elementStyle) && isSingleArray(element);
  }

  private static boolean isSingleArray(final String element) {
    final String value = element.trim();
    return value.startsWith("{")
        && value.endsWith("}")
        && elements(value.substring(1, value.length() - 1)).size() == 1;
  }

  private boolean badTrailingComma(final String inner) {
    if (inner == null || IGNORE.equals(trailingArrayComma) || !inner.contains("{")) {
      return false;
    }
    final boolean trailing = TRAILING_COMMA.matcher(inner).find();
    return ALWAYS.equals(trailingArrayComma) ? !trailing : trailing;
  }

  /** The change in bracket depth that a character causes outside a string. */
  private static int bracketStep(final char c) {
    if (c == '(' || c == '{') {
      return 1;
    }
    return c == ')' || c == '}' ? -1 : 0;
  }

  /** Splits the text of an annotation's arguments at the commas outside brackets and strings. */
  private static List<String> elements(final String text) {
    final List<String> parts = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    int depth = 0;
    boolean quoted = false;
    for (final char c : text.toCharArray()) {
      if (c == QUOTE) {
        quoted = !quoted;
      } else if (!quoted) {
        depth += bracketStep(c);
      }
      if (c == ',' && depth == 0 && !quoted) {
        parts.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    if (!current.toString().isBlank()) {
      parts.add(current.toString());
    }
    return parts;
  }
}
