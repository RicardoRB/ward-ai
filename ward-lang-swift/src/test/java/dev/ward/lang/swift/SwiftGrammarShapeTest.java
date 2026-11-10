package dev.ward.lang.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Checks that the grammar node kinds the Swift rules depend on appear in a parsed sample. A grammar
 * change that renames a kind fails here before the rule fixtures do.
 */
final class SwiftGrammarShapeTest {

  private static final String SAMPLE = "fixtures/swift-structural/grammar-shapes.swift";

  @Test
  void sampleParsesWithoutSyntaxErrors() throws IOException {
    final ParseResult result = parse();

    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  void sampleContainsTheNodeKindsTheRulesDependOn() throws IOException {
    final Set<String> kinds = kinds(parse().tree());

    assertThat(kinds)
        .contains(
            "top_level",
            "protocol_declaration",
            "struct_declaration",
            "struct_name",
            "extension_declaration",
            "type_identifier",
            "class_declaration",
            "class_name",
            "union_style_enum",
            "raw_value_style_enum",
            "enum_name",
            "enum_case_name",
            "function_declaration",
            "function_name",
            "function_signature",
            "parameter_clause",
            "parameter_list",
            "parameter",
            "initializer_declaration",
            "initializer_head",
            "identifier_pattern",
            "declaration_identifier",
            "if_statement",
            "else_clause",
            "guard_statement",
            "for_in_statement",
            "while_statement",
            "repeat_while_statement",
            "switch_statement",
            "case_label",
            "conditional_operator",
            "binary_operator",
            "do_statement",
            "catch_clause",
            "closure_expression");
  }

  @Test
  void sampleHasTheExpectedNumberOfDecisionOperators() throws IOException {
    final long operators = count(parse().tree(), "binary_operator", "&&", "||");

    assertThat(operators).isEqualTo(2);
  }

  private static ParseResult parse() throws IOException {
    return new AntlrLanguageFrontend()
        .parse(
            new SourceUnit(
                "grammar-shapes.swift", AnalysisLanguage.SWIFT, SwiftRuleRuns.read(SAMPLE)));
  }

  private static Set<String> kinds(final SyntaxNode node) {
    final Set<String> kinds = new HashSet<>();
    collect(node, kinds);
    return kinds;
  }

  private static void collect(final SyntaxNode node, final Set<String> kinds) {
    kinds.add(node.kind());
    for (final SyntaxNode child : node.children()) {
      collect(child, kinds);
    }
  }

  /** Counts nodes of {@code kind} whose text is one of the {@code texts}. */
  private static long count(final SyntaxNode node, final String kind, final String... texts) {
    final long own = node.kind().equals(kind) && List.of(texts).contains(text(node)) ? 1 : 0;
    long total = own;
    for (final SyntaxNode child : node.children()) {
      total += count(child, kind, texts);
    }
    return total;
  }

  private static String text(final SyntaxNode node) {
    final String text = node.fields().get("text");
    if (text != null) {
      return text;
    }
    final StringBuilder builder = new StringBuilder();
    for (final SyntaxNode child : node.children()) {
      builder.append(text(child));
    }
    return builder.toString();
  }
}
