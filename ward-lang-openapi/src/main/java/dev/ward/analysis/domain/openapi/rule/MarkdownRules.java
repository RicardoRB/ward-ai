package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** The rules that forbid script-like content in the descriptions and titles of a document. */
final class MarkdownRules {

  private static final Pattern EVAL = Pattern.compile("eval\\(");
  private static final Pattern SCRIPT = Pattern.compile("<script");
  private static final List<String> KEYS = List.of("description", "title");

  private MarkdownRules() {}

  /** {@code no-eval-in-markdown}: no description or title contains {@code eval(}. */
  static List<OpenApiFinding> noEval(final OpenApiDocument document) {
    return forbidden(document, EVAL, "Markdown descriptions must not have \"eval(\".");
  }

  /** {@code no-script-tags-in-markdown}: no description or title contains {@code <script}. */
  static List<OpenApiFinding> noScriptTags(final OpenApiDocument document) {
    return forbidden(document, SCRIPT, "Markdown descriptions must not have \"<script>\" tags.");
  }

  private static List<OpenApiFinding> forbidden(
      final OpenApiDocument document, final Pattern pattern, final String message) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    document
        .walk(View.RESOLVED)
        .filter(node -> node.kind() == DocNode.Kind.OBJECT)
        .forEach(
            node -> {
              for (final DocNode.Entry entry : node.entries()) {
                if (KEYS.contains(entry.key())
                    && entry.value().kind() == DocNode.Kind.STRING
                    && pattern.matcher(entry.value().text()).find()) {
                  findings.add(Findings.atValue(entry.value(), message));
                }
              }
            });
    return findings;
  }
}
