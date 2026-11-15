package dev.ward.analysis.domain.android.rule;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds cleartext traffic that the application allows: {@code android:usesCleartextTraffic="true"}
 * on the manifest's {@code application}, and {@code cleartextTrafficPermitted="true"} on a {@code
 * base-config} or {@code domain-config} of a network security configuration.
 */
public final class NetworkConfigCleartextCheck implements RuleCheck {

  private static final Set<String> CONFIGS = Set.of("base-config", "domain-config");
  private static final String PERMITTED = "cleartextTrafficPermitted";
  private static final String MESSAGE =
      "Cleartext traffic is allowed: use HTTPS and set the flag to false.";

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return AndroidNodes.elements(root).stream()
        .map(NetworkConfigCleartextCheck::flag)
        .flatMap(Optional::stream)
        .map(found -> new RuleMatch(found.span(), MESSAGE))
        .toList();
  }

  private static Optional<SyntaxNode> flag(final SyntaxNode element) {
    final String name = AndroidNodes.name(element);
    if ("application".equals(name) && AndroidNodes.androidTrue(element, "usesCleartextTraffic")) {
      return AndroidNodes.attribute(element, AndroidNodes.ANDROID, "usesCleartextTraffic");
    }
    if (CONFIGS.contains(name)) {
      return AndroidNodes.attribute(element, "", PERMITTED)
          .filter(found -> "true".equals(AndroidNodes.value(found)));
    }
    return Optional.empty();
  }
}
