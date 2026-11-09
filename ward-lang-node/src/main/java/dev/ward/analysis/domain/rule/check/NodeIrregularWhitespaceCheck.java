package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a run of whitespace that is neither a tab nor a space, like ESLint {@code
 * no-irregular-whitespace}. A finding is placed just after its run, and a finding inside a string,
 * regular expression, template, comment or JSX text is dropped when that kind is skipped.
 */
public final class NodeIrregularWhitespaceCheck implements RuleCheck {

  private static final Pattern RUN =
      Pattern.compile(
          "[\\f\\u000B\\u0085\\uFEFF\\u00A0\\u1680\\u180E\\u2000-\\u200B\\u202F\\u205F\\u3000]+");
  private static final Pattern IRREGULAR =
      Pattern.compile(
          "[\\f\\u000B\\u0085\\uFEFF\\u00A0\\u1680\\u180E\\u2000-\\u200B\\u202F\\u205F\\u3000\\u2028\\u2029]");
  private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|[\\n\\r\\u2028\\u2029]");
  private static final String STRING = "StringLiteral";
  private static final String REGEX = "RegularExpressionLiteral";
  private static final String COMMENT = "comment";
  private static final String JSX_TEXT = "JsxText";
  private static final String ATOM = "TemplateStringAtom";
  private static final String ESCAPE_ATOM = "TemplateStringEscapeAtom";
  private static final String LINE_SEPARATOR = " ";
  private static final String PARAGRAPH_SEPARATOR = " ";
  private static final int ONE = 1;
  private static final int TWO = 2;

  /** Which kinds of text a finding may be dropped in. */
  public record Skips(
      boolean comments, boolean jsxText, boolean regexps, boolean strings, boolean templates) {}

  /** A finding: its line, and its column counted from zero, as ESLint reports it. */
  private record Finding(int line, int column) {}

  /** A range in ESLint's coordinates: lines from one, columns from zero, end exclusive. */
  private record Range(int startLine, int startColumn, int endLine, int endColumn) {

    boolean keeps(final Finding finding) {
      return finding.line() < startLine
          || finding.line() == startLine && finding.column() < startColumn
          || finding.line() == endLine && finding.column() >= endColumn
          || finding.line() > endLine;
    }
  }

  /** The lines of a source text and the offset where each starts. */
  private record Lines(List<String> text, List<Integer> starts, List<String> breaks) {}

  private final Skips skips;
  private final String message;

  /**
   * Creates the check.
   *
   * @param skips the kinds of text a finding may be dropped in
   * @param message the message of each finding
   */
  public NodeIrregularWhitespaceCheck(final Skips skips, final String message) {
    this.skips = Objects.requireNonNull(skips, "skips");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the irregular-whitespace check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    final String source = unit.text();
    if (!IRREGULAR.matcher(source).find()) {
      return List.of();
    }
    final Lines lines = split(source);
    final List<Finding> findings = new ArrayList<>();
    addRuns(lines, findings);
    addTerminators(lines, findings);
    final List<Range> ignored = ignoredRanges(root);
    return toMatches(findings, ignored, lines);
  }

  private static Lines split(final String source) {
    final List<String> text = new ArrayList<>();
    final List<Integer> starts = new ArrayList<>();
    final List<String> breaks = new ArrayList<>();
    final Matcher matcher = LINE_BREAK.matcher(source);
    int start = 0;
    while (matcher.find()) {
      text.add(source.substring(start, matcher.start()));
      starts.add(start);
      breaks.add(matcher.group());
      start = matcher.end();
    }
    text.add(source.substring(start));
    starts.add(start);
    return new Lines(text, starts, breaks);
  }

  private void addRuns(final Lines lines, final List<Finding> findings) {
    for (int index = 0; index < lines.text().size(); index++) {
      final Matcher run = RUN.matcher(lines.text().get(index));
      while (run.find()) {
        findings.add(new Finding(index + ONE, run.start()));
      }
    }
  }

  /** A line or paragraph separator is reported at the end of the line it ends. */
  private static void addTerminators(final Lines lines, final List<Finding> findings) {
    for (int index = 0; index < lines.breaks().size(); index++) {
      final String separator = lines.breaks().get(index);
      if (LINE_SEPARATOR.equals(separator) || PARAGRAPH_SEPARATOR.equals(separator)) {
        findings.add(new Finding(index + ONE, lines.text().get(index).length()));
      }
    }
  }

  private List<Range> ignoredRanges(final SyntaxNode root) {
    final List<Range> ranges = new ArrayList<>();
    final List<SyntaxNode> run = new ArrayList<>();
    for (final SyntaxNode leaf : LeafText.leaves(root)) {
      if (isAtom(leaf)) {
        run.add(leaf);
        continue;
      }
      addTemplateRange(run, ranges);
      run.clear();
      addSingleRange(leaf, ranges);
    }
    addTemplateRange(run, ranges);
    return ranges;
  }

  private static boolean isAtom(final SyntaxNode leaf) {
    return ATOM.equals(leaf.kind()) || ESCAPE_ATOM.equals(leaf.kind());
  }

  /** A run of template atoms is one template element, which is skipped when it is irregular. */
  private void addTemplateRange(final List<SyntaxNode> atoms, final List<Range> ranges) {
    if (atoms.isEmpty() || !skips.templates()) {
      return;
    }
    final StringBuilder text = new StringBuilder();
    atoms.forEach(atom -> text.append(LeafText.of(atom)));
    if (IRREGULAR.matcher(text).find()) {
      ranges.add(range(atoms.get(0).span(), atoms.get(atoms.size() - ONE).span()));
    }
  }

  private void addSingleRange(final SyntaxNode leaf, final List<Range> ranges) {
    if (skippedKind(leaf.kind()) && IRREGULAR.matcher(LeafText.of(leaf)).find()) {
      ranges.add(range(leaf.span(), leaf.span()));
    }
  }

  private boolean skippedKind(final String kind) {
    return switch (kind) {
      case STRING -> skips.strings();
      case REGEX -> skips.regexps();
      case COMMENT -> skips.comments();
      case JSX_TEXT -> skips.jsxText();
      default -> false;
    };
  }

  private static Range range(final Span first, final Span last) {
    return new Range(
        first.startLine(), first.startColumn() - ONE, last.endLine(), last.endColumn() - ONE);
  }

  private List<RuleMatch> toMatches(
      final List<Finding> findings, final List<Range> ignored, final Lines lines) {
    final List<Finding> kept =
        findings.stream()
            .filter(finding -> ignored.stream().allMatch(range -> range.keeps(finding)))
            .sorted(Comparator.comparingInt(Finding::line).thenComparingInt(Finding::column))
            .toList();
    final List<RuleMatch> matches = new ArrayList<>();
    for (final Finding finding : kept) {
      final int offset = lines.starts().get(finding.line() - ONE) + finding.column();
      matches.add(
          new RuleMatch(
              new Span(
                  finding.line(),
                  finding.column() + ONE,
                  finding.line(),
                  finding.column() + TWO,
                  offset,
                  offset + ONE),
              message));
    }
    return List.copyOf(matches);
  }
}
