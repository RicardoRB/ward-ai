package dev.ward.analysis.domain.rule.check;

import java.util.Map;
import java.util.regex.Matcher;

/** The name conditions of a decorated-class check: the interface a method needs and the alias. */
final class DecoratedNames {

  private static final String ARIA_PREFIX = "aria-";

  private DecoratedNames() {}

  /** Tells whether the node meets the name conditions. */
  static boolean holds(
      final DecoratedSite site, final DecoratedClassCheck.NameCondition condition) {
    return missingInterfaceOk(site, condition.heritageMissing())
        && (condition.alias().isEmpty() || aliasReported(site, condition));
  }

  /** Tells whether the table is empty, or the node's text needs an interface its class lacks. */
  private static boolean missingInterfaceOk(
      final DecoratedSite site, final Map<String, String> heritageMissing) {
    if (heritageMissing.isEmpty()) {
      return true;
    }
    final String interfaceName = heritageMissing.get(site.text);
    return interfaceName != null && !DecoratedTree.implementsName(site.cls, interfaceName);
  }

  /** Tells whether the node's alias is present and not allowed by the options. */
  private static boolean aliasReported(
      final DecoratedSite site, final DecoratedClassCheck.NameCondition condition) {
    final Matcher matcher = condition.alias().get().matcher(site.text);
    return matcher.find() && reportable(matcher.group(1), site, condition);
  }

  private static boolean reportable(
      final String alias,
      final DecoratedSite site,
      final DecoratedClassCheck.NameCondition condition) {
    return !condition.allowed().contains(alias)
        && !(condition.aria()
            && alias.startsWith(ARIA_PREFIX)
            && DecoratedTree.camelCase(alias).equals(site.memberName()));
  }
}
