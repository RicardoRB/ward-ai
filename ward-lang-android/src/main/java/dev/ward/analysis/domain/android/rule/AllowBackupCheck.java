package dev.ward.analysis.domain.android.rule;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.List;
import java.util.Optional;

/**
 * Finds an {@code application} that lets the system back up all its data: {@code
 * android:allowBackup="true"}, or no {@code allowBackup} with neither {@code fullBackupContent} nor
 * {@code dataExtractionRules} to say what is saved.
 */
public final class AllowBackupCheck implements RuleCheck {

  private static final String ALLOW = "allowBackup";
  private static final String MESSAGE =
      "The application data can be backed up: set android:allowBackup=\"false\" or limit it with"
          + " android:dataExtractionRules.";

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return AndroidNodes.elements(root).stream()
        .filter(element -> "application".equals(AndroidNodes.name(element)))
        .map(AllowBackupCheck::finding)
        .flatMap(Optional::stream)
        .map(found -> new RuleMatch(found.span(), MESSAGE))
        .toList();
  }

  private static Optional<SyntaxNode> finding(final SyntaxNode application) {
    final Optional<SyntaxNode> allow =
        AndroidNodes.attribute(application, AndroidNodes.ANDROID, ALLOW);
    if (allow.isPresent()) {
      return allow.filter(found -> "true".equals(AndroidNodes.value(found)));
    }
    final boolean limited =
        AndroidNodes.attribute(application, AndroidNodes.ANDROID, "fullBackupContent").isPresent()
            || AndroidNodes.attribute(application, AndroidNodes.ANDROID, "dataExtractionRules")
                .isPresent();
    return limited ? Optional.empty() : Optional.of(application);
  }
}
