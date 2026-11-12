package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** The rules that forbid script-like content in the descriptions of a document. */
final class MarkdownRules {

  private static final Pattern EVAL = Pattern.compile("eval\\(");
  private static final Pattern SCRIPT = Pattern.compile("<script");
  private static final String DESCRIPTION = "description";
  private static final Set<String> SKIPPED = Set.of("example", "examples");

  private MarkdownRules() {}

  /** {@code no-eval-in-markdown}: no description contains {@code eval(}. */
  static List<OpenApiFinding> noEval(final OpenApiDocument document) {
    return forbidden(document, EVAL, "eval\\(");
  }

  /** {@code no-script-tags-in-markdown}: no description contains {@code <script}. */
  static List<OpenApiFinding> noScriptTags(final OpenApiDocument document) {
    return forbidden(document, SCRIPT, "<script");
  }

  private static List<OpenApiFinding> forbidden(
      final OpenApiDocument document, final Pattern pattern, final String shown) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode description : descriptions(document.root())) {
      if (pattern.matcher(description.text()).find()) {
        findings.add(
            Findings.atValue(
                description, "description contains content with `" + shown + "`, forbidden"));
      }
    }
    return findings;
  }

  /**
   * The string values of every {@code description} key, in file order, outside examples, which
   * vacuum does not index.
   */
  private static List<DocNode> descriptions(final DocNode root) {
    final List<DocNode> found = new ArrayList<>();
    final Deque<DocNode> stack = new ArrayDeque<>();
    stack.push(root);
    while (!stack.isEmpty()) {
      final DocNode node = stack.pop();
      final List<DocNode.Entry> entries = node.entries();
      for (int index = entries.size() - 1; index >= 0; index--) {
        final DocNode.Entry entry = entries.get(index);
        if (!SKIPPED.contains(entry.key())) {
          stack.push(entry.value());
        }
      }
      for (int index = node.items().size() - 1; index >= 0; index--) {
        stack.push(node.items().get(index));
      }
      if (node.kind() == DocNode.Kind.OBJECT) {
        node.get(DESCRIPTION)
            .filter(text -> text.kind() == DocNode.Kind.STRING)
            .ifPresent(found::add);
      }
    }
    return found;
  }
}
