package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@code description-duplication}: no description or summary repeats an earlier one. */
final class DuplicationRules {

  private DuplicationRules() {}

  /**
   * Reports a description that repeats an earlier description, a summary that repeats an earlier
   * summary, and a summary that repeats a description, as vacuum does.
   */
  static List<OpenApiFinding> descriptions(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final Map<String, DocNode> descriptions = new HashMap<>();
    final Map<String, DocNode> summaries = new HashMap<>();
    for (final DocNode description : MarkdownRules.stringsNamed(document.root(), "description")) {
      seen(descriptions, description, "Description", findings);
    }
    for (final DocNode summary : MarkdownRules.stringsNamed(document.root(), "summary")) {
      seen(summaries, summary, "Summary", findings);
      if (!descriptions.isEmpty()) {
        seen(descriptions, summary, "Description", findings);
      }
    }
    return findings;
  }

  private static void seen(
      final Map<String, DocNode> seen,
      final DocNode node,
      final String label,
      final List<OpenApiFinding> findings) {
    final DocNode first = seen.putIfAbsent(node.text(), node);
    if (first != null && !first.equals(node)) {
      findings.add(
          Findings.atValue(
              node,
              label
                  + " at line `"
                  + node.span().startLine()
                  + "` is a duplicate of line `"
                  + first.span().startLine()
                  + "`"));
    }
  }
}
