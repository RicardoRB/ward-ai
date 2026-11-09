package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports an availability condition or attribute whose version of a platform is at or below the
 * configured deployment target of that platform, because it is always satisfied, like SwiftLint's
 * {@code deployment_target}. The check does nothing for a platform without a configured target.
 */
public final class SwiftDeploymentTargetCheck implements RuleCheck {

  private static final String CONDITION = "availability_condition";
  private static final String ATTRIBUTE = "attribute";
  private static final Pattern CONDITION_TEXT =
      Pattern.compile("^#(?:available|unavailable)\\((.*)\\)$");
  private static final Pattern ATTRIBUTE_TEXT = Pattern.compile("^@available\\((.*)\\)$");
  private static final Pattern PLATFORM_VERSION = Pattern.compile("^([A-Za-z]+)([0-9][0-9.]*)$");
  private static final Pattern VERSION = Pattern.compile("[0-9][0-9.]*");
  private static final String ARGUMENT_SEPARATOR = ",";
  private static final String VERSION_SEPARATOR = "\\.";

  private final String message;
  private final Map<String, String> targets;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   * @param targets the deployment target of each platform name, such as {@code iOS}, by name
   */
  public SwiftDeploymentTargetCheck(final String message, final Map<String, String> targets) {
    this.message = message;
    this.targets = Map.copyOf(targets);
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : SwiftTrees.descendants(root, CONDITION)) {
      addAll(node, CONDITION_TEXT, matches);
    }
    for (final SyntaxNode node : SwiftTrees.descendants(root, ATTRIBUTE)) {
      addAll(node, ATTRIBUTE_TEXT, matches);
    }
    return List.copyOf(matches);
  }

  private void addAll(final SyntaxNode node, final Pattern shape, final List<RuleMatch> matches) {
    final Matcher matcher = shape.matcher(SwiftTrees.compact(node).replaceAll("\\s", ""));
    if (matcher.matches()) {
      for (final String argument : matcher.group(1).split(ARGUMENT_SEPARATOR)) {
        if (satisfiedByTarget(argument)) {
          matches.add(SwiftTrees.at(node, message));
        }
      }
    }
  }

  /** Whether an availability argument such as {@code iOS12.0} is at or below the target. */
  private boolean satisfiedByTarget(final String argument) {
    final Matcher platformVersion = PLATFORM_VERSION.matcher(argument);
    if (!platformVersion.matches()) {
      return false;
    }
    final String target = targets.get(platformVersion.group(1));
    return target != null
        && VERSION.matcher(target).matches()
        && compare(platformVersion.group(2), target) <= 0;
  }

  /** Compares dotted versions numerically, a missing part counting as zero. */
  static int compare(final String left, final String right) {
    final String[] a = left.split(VERSION_SEPARATOR);
    final String[] b = right.split(VERSION_SEPARATOR);
    for (int i = 0; i < Math.max(a.length, b.length); i++) {
      final int x = i < a.length ? Integer.parseInt(a[i]) : 0;
      final int y = i < b.length ? Integer.parseInt(b[i]) : 0;
      if (x != y) {
        return Integer.compare(x, y);
      }
    }
    return 0;
  }
}
