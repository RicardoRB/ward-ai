package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.stream.Collectors;

/** Renders a parsed tree as compact text so that tests compare its shape in one line. */
final class YamlTrees {

  /** The offset of {@code 12} in {@code "a:\n b: 12\n"}. */
  static final int OFFSET_OF_TWELVE = 8;

  private YamlTrees() {}

  static String at(final Span span) {
    return span.startLine() + ":" + span.startColumn();
  }

  static String end(final Span span) {
    return span.endLine() + ":" + span.endColumn();
  }

  static String dump(final String text) {
    final YamlResult result = YamlParser.parse(text);
    if (!result.valid()) {
      final YamlDiagnostic problem = result.diagnostics().getFirst();
      return "ERROR " + problem.line() + ":" + problem.column() + " " + problem.message();
    }
    return dump(result.tree());
  }

  static String dump(final SyntaxNode node) {
    return switch (node.kind()) {
      case YamlSyntax.OBJECT ->
          node.children().stream().map(YamlTrees::entry).collect(Collectors.joining(",", "{", "}"));
      case YamlSyntax.ARRAY ->
          node.children().stream().map(YamlTrees::dump).collect(Collectors.joining(",", "[", "]"));
      case YamlSyntax.STRING -> "\"" + node.fields().get(YamlSyntax.VALUE) + "\"";
      case YamlSyntax.NULL -> "~";
      default -> node.fields().get(YamlSyntax.VALUE);
    };
  }

  private static String entry(final SyntaxNode entry) {
    return entry.children().getFirst().fields().get(YamlSyntax.VALUE)
        + "="
        + dump(entry.children().getLast());
  }
}
