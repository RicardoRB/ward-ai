package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A test-only language. A line {@code import X} refers to the file {@code X.kt}, {@code export N}
 * exports the symbol {@code N}, and {@code declare N} declares it without exporting. Its one rule
 * reports each import whose target exports nothing, so it reads the exports of the files it
 * imports.
 */
final class DependencyFixtureLanguage {
  static final AnalysisLanguage LANGUAGE = AnalysisLanguage.KOTLIN;
  static final String RULE_ID = "ward:kotlin/unexported-import";
  static final RuleDefinition RULE =
      new RuleDefinition(
          RULE_ID, LANGUAGE, RuleCapability.SYMBOLS, AnalysisSeverity.WARNING, "fixture");
  private static final String SUFFIX = ".kt";
  private static final Pattern LINE = Pattern.compile("(import|export|declare) (\\w+)");

  /** The paths whose trees the frontend built, summaries included. */
  final List<String> parsed = new ArrayList<>();

  /** The paths the rule ran on. */
  final List<String> ruleRuns = new ArrayList<>();

  FrontendSupport frontend() {
    return new FrontendSupport(
        LANGUAGE, this::parse, Set.of(RuleCapability.SYNTAX, RuleCapability.SYMBOLS));
  }

  List<DependencySupport> dependencies() {
    return List.of(new DependencySupport(LANGUAGE, this::summarize, this::resolve));
  }

  RuleMatcher matcher() {
    return new DependencyFixtureRule(ruleRuns);
  }

  private ParseResult parse(final SourceUnit unit) {
    parsed.add(unit.path());
    final List<SyntaxNode> children = new ArrayList<>();
    final String[] lines = unit.text().split("\n", -1);
    int offset = 0;
    for (int index = 0; index < lines.length; index++) {
      final String line = lines[index];
      final Matcher matcher = LINE.matcher(line);
      if (matcher.matches()) {
        children.add(
            new SyntaxNode(
                matcher.group(1),
                span(index + 1, offset, line.length()),
                Map.of("name", matcher.group(2)),
                List.of()));
      }
      offset += line.length() + 1;
    }
    return new ParseResult(
        new SyntaxNode(unit.path(), span(1, 0, 1), Map.of(), children), List.of());
  }

  private FileSummary summarize(final SourceUnit unit, final SyntaxNode tree) {
    return new FileSummary(
        unit.path(),
        symbols(tree, "export"),
        tree.children().stream()
            .filter(node -> "import".equals(node.kind()))
            .map(node -> new Reference(node.fields().get("name"), node.span()))
            .toList(),
        symbols(tree, "declare"));
  }

  private List<DependencyEdge> resolve(final List<FileSummary> summaries) {
    final Set<String> paths = summaries.stream().map(FileSummary::path).collect(Collectors.toSet());
    return summaries.stream()
        .flatMap(
            summary ->
                summary.imports().stream()
                    .filter(reference -> paths.contains(target(reference)))
                    .map(
                        reference ->
                            new DependencyEdge(summary.path(), target(reference), reference)))
        .toList();
  }

  private static List<Symbol> symbols(final SyntaxNode tree, final String kind) {
    return tree.children().stream()
        .filter(node -> kind.equals(node.kind()))
        .map(node -> new Symbol(node.fields().get("name"), node.span()))
        .toList();
  }

  /** A unit of the fixture language at {@code path} with {@code text}. */
  static SourceUnit unit(final String path, final String text) {
    return new SourceUnit(path, LANGUAGE, text);
  }

  private static String target(final Reference reference) {
    return reference.specifier() + SUFFIX;
  }

  private static Span span(final int line, final int offset, final int length) {
    final int width = Math.max(1, length);
    return new Span(line, 1, line, width, offset, offset + width);
  }
}
