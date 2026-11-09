package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Applies the class, member, node and scope conditions of a decorated-class check. */
final class DecoratedFilters {

  private final DecoratedClassCheck.Filters filters;

  DecoratedFilters(final DecoratedClassCheck.Filters filters) {
    this.filters = filters;
  }

  /** Tells whether the site meets every condition of the check. */
  boolean holds(final DecoratedSite site) {
    return enclosingOk(site)
        && memberOk(site)
        && nodeOk(site)
        && scopeOk(site)
        && DecoratedNames.holds(site, filters.name());
  }

  private boolean enclosingOk(final DecoratedSite site) {
    final DecoratedClassCheck.ClassCondition condition = filters.enclosing();
    final boolean decorated =
        condition.decorator().map(p -> DecoratedTree.anyDecorator(site.cls, p)).orElse(true);
    final boolean entryOk =
        condition.entries().isEmpty() || anyEntryHolds(site, condition.entries());
    final boolean notImplementing =
        condition
            .implementsNot()
            .map(name -> !DecoratedTree.implementsName(site.cls, name))
            .orElse(true);
    return decorated && entryOk && notImplementing;
  }

  private static boolean anyEntryHolds(
      final DecoratedSite site, final Map<Pattern, Pattern> entries) {
    return entries.entrySet().stream()
        .anyMatch(
            entry ->
                DecoratedTree.anyDecorator(site.cls, entry.getKey())
                    && entry.getValue().matcher(site.text).find());
  }

  private boolean memberOk(final DecoratedSite site) {
    final DecoratedClassCheck.MemberCondition condition = filters.member();
    final boolean decorated =
        condition.decorator().map(p -> DecoratedTree.anyDecorator(site.element, p)).orElse(true);
    final boolean has =
        condition.has().isEmpty() || DecoratedTree.hasChildOf(site.member, condition.has());
    final boolean notText =
        condition
            .notText()
            .map(
                p -> site.member.isEmpty() || !p.matcher(SyntaxWalk.text(site.member.get())).find())
            .orElse(true);
    return decorated && has && notText;
  }

  private boolean nodeOk(final DecoratedSite site) {
    final DecoratedClassCheck.NodeCondition condition = filters.node();
    final boolean text = condition.text().map(p -> p.matcher(site.text).find()).orElse(true);
    final boolean exempt = condition.exempt().map(p -> !p.matcher(site.text).find()).orElse(true);
    final boolean placed =
        !condition.inClassDecorator() || DecoratedTree.inClassDecorator(site.ancestors);
    return text && exempt && placed && notInsideOk(site, condition.notInsideText());
  }

  private static boolean notInsideOk(final DecoratedSite site, final Optional<Pattern> pattern) {
    return pattern.isEmpty()
        || site.ancestors.stream()
            .map(SyntaxWalk::text)
            .noneMatch(text -> pattern.get().matcher(text).find());
  }

  private boolean scopeOk(final DecoratedSite site) {
    final DecoratedClassCheck.ScopeCondition condition = filters.scope();
    return insideOk(site, condition.inside())
        && parentOk(site, condition.parent())
        && (condition.notChild().isEmpty()
            || !DecoratedTree.hasChildOf(Optional.of(site.node), condition.notChild()));
  }

  private static boolean insideOk(final DecoratedSite site, final Set<String> kinds) {
    return kinds.isEmpty()
        || site.ancestors.stream().anyMatch(ancestor -> kinds.contains(ancestor.kind()));
  }

  private static boolean parentOk(final DecoratedSite site, final Set<String> kinds) {
    if (kinds.isEmpty()) {
      return true;
    }
    final SyntaxNode parent =
        site.ancestors.isEmpty() ? null : site.ancestors.get(site.ancestors.size() - 1);
    return parent != null && kinds.contains(parent.kind());
  }
}
