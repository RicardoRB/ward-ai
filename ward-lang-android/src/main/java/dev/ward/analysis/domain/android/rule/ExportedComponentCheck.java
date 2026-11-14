package dev.ward.analysis.domain.android.rule;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.List;
import java.util.Optional;

/**
 * Finds a manifest component of one kind ({@code service}, {@code receiver} or {@code provider})
 * that other applications can reach and that asks for no permission. A component is exported with
 * {@code android:exported="true"}, or, for a service or receiver, with an intent filter and no
 * {@code exported} attribute. A permission is {@code android:permission}; a provider also accepts a
 * read or a write permission, as Android Lint does (a read permission alone is its separate
 * ProviderReadPermissionOnly check), or a {@code path-permission} child.
 */
public final class ExportedComponentCheck implements RuleCheck {

  private static final String PROVIDER = "provider";
  private static final String EXPORTED = "exported";
  private static final String PERMISSION = "permission";

  private final String component;

  /**
   * Creates the check for one kind of component.
   *
   * @param component {@code service}, {@code receiver} or {@code provider}
   */
  public ExportedComponentCheck(final String component) {
    this.component = component;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return AndroidNodes.elements(root).stream()
        .filter(element -> component.equals(AndroidNodes.name(element)))
        .filter(this::exported)
        .filter(element -> !protectedByPermission(element))
        .map(this::match)
        .toList();
  }

  private RuleMatch match(final SyntaxNode element) {
    final SyntaxNode at =
        AndroidNodes.attribute(element, AndroidNodes.ANDROID, EXPORTED).orElse(element);
    return new RuleMatch(
        at.span(),
        "Exported "
            + component
            + " requires no permission: add android:permission or set android:exported=\"false\".");
  }

  private boolean exported(final SyntaxNode element) {
    final Optional<SyntaxNode> declared =
        AndroidNodes.attribute(element, AndroidNodes.ANDROID, EXPORTED);
    if (declared.isPresent()) {
      return "true".equals(AndroidNodes.value(declared.get()));
    }
    return !PROVIDER.equals(component) && AndroidNodes.hasChild(element, "intent-filter");
  }

  private boolean protectedByPermission(final SyntaxNode element) {
    return has(element, PERMISSION)
        || PROVIDER.equals(component)
            && (has(element, "readPermission")
                || has(element, "writePermission")
                || AndroidNodes.hasChild(element, "path-permission"));
  }

  private static boolean has(final SyntaxNode element, final String local) {
    return AndroidNodes.attribute(element, AndroidNodes.ANDROID, local)
        .map(AndroidNodes::value)
        .filter(value -> !value.isBlank())
        .isPresent();
  }
}
