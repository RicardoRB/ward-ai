package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.ArrayList;
import java.util.List;

/** The rule about {@code webhooks} that went away. */
final class WebhookRules {

  private WebhookRules() {}

  /** The webhook rules. */
  static List<BreakingRule> rules() {
    return List.of(RowRule.of("webhook-removed", WebhookRules::removed));
  }

  private static List<BreakingFinding> removed(final DocumentPair pair) {
    final List<String> kept =
        pair.revision().parts().webhooks().stream().map(DocNode.Entry::key).toList();
    final DocNode at = pair.revision().root().get("webhooks").orElse(pair.revision().root());
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final DocNode.Entry webhook : pair.base().parts().webhooks()) {
      if (!kept.contains(webhook.key())) {
        findings.add(
            new BreakingFinding(
                RowRule.PREFIX + "webhook-removed",
                "webhook " + webhook.key() + " was removed",
                "",
                "",
                at));
      }
    }
    return findings;
  }
}
