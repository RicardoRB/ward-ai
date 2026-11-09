package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports the nodes of the given kinds that sit in a decorated class, such as an Angular component
 * with {@code @Component(...)}. The class decorators, the member's decorators and shape, the node's
 * own text and place, and the alias of a decorator argument are each optional; a condition that is
 * set must hold for the node to be reported.
 */
public final class DecoratedClassCheck implements RuleCheck {

  private static final String CLASS = "classDeclaration";
  private static final String CLASS_ELEMENT = "classElement";
  private static final String MEMBER = "propertyMemberDeclaration";
  private static final String DECORATOR_LIST = "decoratorList";
  private static final String DECORATOR = "decorator";
  private static final String HERITAGE = "classHeritage";
  private static final String IDENTIFIER = "Identifier";
  private static final String PROPERTY_NAME = "propertyName";
  private static final String ARIA_PREFIX = "aria-";

  private final Set<String> kinds;
  private final Filters filters;
  private final String message;

  /**
   * The conditions on the enclosing class: a class decorator that must match, pairs of a class
   * decorator and the node text it allows, and an interface the class must not implement.
   *
   * @param decorator a pattern a class decorator must match, if present
   * @param entries pairs of a class decorator pattern and a node text pattern; the node must match
   *     one pair, if any pair is given
   * @param implementsNot the interface name the class must not implement, if present
   */
  public record ClassCondition(
      Optional<Pattern> decorator, Map<Pattern, Pattern> entries, Optional<String> implementsNot) {

    /** Copies the entries. */
    public ClassCondition {
      entries = Map.copyOf(entries);
    }
  }

  /**
   * The conditions on the class member that holds the node.
   *
   * @param decorator a pattern a decorator of the member must match, if present
   * @param has kinds of which the member must have a direct child, if not empty
   * @param notText a pattern the member's text must not match, if present
   */
  public record MemberCondition(
      Optional<Pattern> decorator, Set<String> has, Optional<Pattern> notText) {

    /** Copies the kinds. */
    public MemberCondition {
      has = Set.copyOf(has);
    }
  }

  /**
   * The conditions on the node's own text and on what the node contains.
   *
   * @param text a pattern the node's text must match, if present
   * @param exempt a pattern whose matches are never reported, if present
   * @param inClassDecorator whether the node must sit inside a decorator of its class
   * @param notInsideText a pattern no enclosing node's text may match, if present
   */
  public record NodeCondition(
      Optional<Pattern> text,
      Optional<Pattern> exempt,
      boolean inClassDecorator,
      Optional<Pattern> notInsideText) {}

  /**
   * The conditions on the place of the node in the tree.
   *
   * @param inside kinds of which some ancestor must be, if not empty
   * @param parent kinds of which the parent must be, if not empty
   * @param notChild kinds of which the node must have no direct child
   */
  public record ScopeCondition(Set<String> inside, Set<String> parent, Set<String> notChild) {

    /** Copies the sets. */
    public ScopeCondition {
      inside = Set.copyOf(inside);
      parent = Set.copyOf(parent);
      notChild = Set.copyOf(notChild);
    }
  }

  /**
   * The conditions on the name the node carries.
   *
   * @param heritageMissing for a node whose text is a key, the interface its class must implement;
   *     the node is reported only when the class does not implement it, and only for these keys
   * @param alias a pattern whose first group is the alias of the node, if present
   * @param allowed aliases that are never reported
   * @param aria whether an {@code aria-*} alias is allowed when it names the member in camel case
   */
  public record NameCondition(
      Map<String, String> heritageMissing,
      Optional<Pattern> alias,
      Set<String> allowed,
      boolean aria) {

    /** Copies the maps and sets. */
    public NameCondition {
      heritageMissing = Map.copyOf(heritageMissing);
      allowed = Set.copyOf(allowed);
    }
  }

  /**
   * All the conditions of the check.
   *
   * @param enclosing the conditions on the enclosing class
   * @param member the conditions on the class member
   * @param node the conditions on the node's text
   * @param scope the conditions on the node's place
   * @param name the conditions on the node's name
   */
  public record Filters(
      ClassCondition enclosing,
      MemberCondition member,
      NodeCondition node,
      ScopeCondition scope,
      NameCondition name) {}

  /**
   * Creates the check.
   *
   * @param kinds the node kinds to look at
   * @param filters the conditions every reported node meets
   * @param message the message of each finding
   */
  public DecoratedClassCheck(final Set<String> kinds, final Filters filters, final String message) {
    this.kinds = Set.copyOf(kinds);
    this.filters = filters;
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, kinds)) {
      if (holds(new Site(visit))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private boolean holds(final Site site) {
    return enclosingOk(site) && memberOk(site) && nodeOk(site) && scopeOk(site) && nameOk(site);
  }

  private boolean enclosingOk(final Site site) {
    final ClassCondition condition = filters.enclosing();
    if (condition.decorator().isPresent() && !anyDecorator(site.cls, condition.decorator().get())) {
      return false;
    }
    if (!condition.entries().isEmpty() && noEntryHolds(site, condition.entries())) {
      return false;
    }
    return condition.implementsNot().isEmpty()
        || !implementsName(site.cls, condition.implementsNot().get());
  }

  private static boolean noEntryHolds(final Site site, final Map<Pattern, Pattern> entries) {
    return entries.entrySet().stream()
        .noneMatch(
            entry ->
                anyDecorator(site.cls, entry.getKey())
                    && entry.getValue().matcher(site.text).find());
  }

  private boolean memberOk(final Site site) {
    final MemberCondition condition = filters.member();
    if (condition.decorator().isPresent()
        && !anyDecorator(site.element, condition.decorator().get())) {
      return false;
    }
    if (!condition.has().isEmpty() && !hasChildOf(site.member, condition.has())) {
      return false;
    }
    return condition.notText().isEmpty()
        || site.member.isEmpty()
        || !condition.notText().get().matcher(SyntaxWalk.text(site.member.get())).find();
  }

  private boolean nodeOk(final Site site) {
    final NodeCondition condition = filters.node();
    if (condition.text().isPresent() && !condition.text().get().matcher(site.text).find()) {
      return false;
    }
    if (condition.exempt().isPresent() && condition.exempt().get().matcher(site.text).find()) {
      return false;
    }
    if (condition.inClassDecorator() && !inClassDecorator(site.ancestors)) {
      return false;
    }
    return condition.notInsideText().isEmpty()
        || site.ancestors.stream()
            .noneMatch(
                ancestor ->
                    condition.notInsideText().get().matcher(SyntaxWalk.text(ancestor)).find());
  }

  private boolean scopeOk(final Site site) {
    final ScopeCondition condition = filters.scope();
    if (!condition.inside().isEmpty()
        && site.ancestors.stream()
            .noneMatch(ancestor -> condition.inside().contains(ancestor.kind()))) {
      return false;
    }
    if (!condition.parent().isEmpty()
        && (site.ancestors.isEmpty()
            || !condition
                .parent()
                .contains(site.ancestors.get(site.ancestors.size() - 1).kind()))) {
      return false;
    }
    return condition.notChild().isEmpty()
        || !hasChildOf(Optional.of(site.node), condition.notChild());
  }

  private boolean nameOk(final Site site) {
    final NameCondition condition = filters.name();
    if (!condition.heritageMissing().isEmpty() && !missingInterface(site, condition)) {
      return false;
    }
    return condition.alias().isEmpty() || aliasReported(site, condition);
  }

  /** Tells whether the node's text is a key whose interface its class does not implement. */
  private static boolean missingInterface(final Site site, final NameCondition condition) {
    final String interfaceName = condition.heritageMissing().get(site.text);
    return interfaceName != null && !implementsName(site.cls, interfaceName);
  }

  /** Tells whether the node's alias is present and not allowed by the options. */
  private static boolean aliasReported(final Site site, final NameCondition condition) {
    final Matcher matcher = condition.alias().get().matcher(site.text);
    if (!matcher.find()) {
      return false;
    }
    final String alias = matcher.group(1);
    if (condition.allowed().contains(alias)) {
      return false;
    }
    return !(condition.aria()
        && alias.startsWith(ARIA_PREFIX)
        && camelCase(alias).equals(site.memberName()));
  }

  /** Converts a kebab-case name to camel case, so {@code aria-label} becomes {@code ariaLabel}. */
  private static String camelCase(final String kebab) {
    final StringBuilder out = new StringBuilder();
    for (final String part : kebab.split("-")) {
      out.append(out.isEmpty() ? part : capitalize(part));
    }
    return out.toString();
  }

  private static String capitalize(final String part) {
    return part.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1);
  }

  private static boolean inClassDecorator(final List<SyntaxNode> ancestors) {
    for (int index = 2; index < ancestors.size(); index++) {
      if (DECORATOR.equals(ancestors.get(index).kind())
          && DECORATOR_LIST.equals(ancestors.get(index - 1).kind())
          && CLASS.equals(ancestors.get(index - 2).kind())) {
        return true;
      }
    }
    return false;
  }

  private static boolean anyDecorator(final Optional<SyntaxNode> owner, final Pattern pattern) {
    return owner.isPresent()
        && owner.get().children().stream()
            .filter(child -> DECORATOR_LIST.equals(child.kind()))
            .flatMap(list -> list.children().stream())
            .filter(child -> DECORATOR.equals(child.kind()))
            .anyMatch(decorator -> pattern.matcher(SyntaxWalk.text(decorator)).find());
  }

  private static boolean hasChildOf(final Optional<SyntaxNode> node, final Set<String> kinds) {
    return node.isPresent()
        && node.get().children().stream().anyMatch(child -> kinds.contains(child.kind()));
  }

  private static boolean implementsName(final Optional<SyntaxNode> cls, final String name) {
    return cls.isPresent()
        && cls.get().children().stream()
            .filter(child -> HERITAGE.equals(child.kind()))
            .flatMap(heritage -> SyntaxWalk.leaves(heritage).stream())
            .anyMatch(
                leaf -> IDENTIFIER.equals(leaf.kind()) && name.equals(leaf.fields().get("text")));
  }

  /** The node under check with the nearest enclosing class, class member and member element. */
  private static final class Site {

    private final SyntaxNode node;
    private final String text;
    private final List<SyntaxNode> ancestors;
    private final Optional<SyntaxNode> cls;
    private final Optional<SyntaxNode> element;
    private final Optional<SyntaxNode> member;

    Site(final SyntaxWalk.Visit visit) {
      this.node = visit.node();
      this.text = SyntaxWalk.text(node);
      this.ancestors = visit.ancestors();
      this.cls = nearest(CLASS, Optional.empty());
      this.element = nearest(CLASS_ELEMENT, Optional.empty());
      this.member = nearest(MEMBER, Optional.of(node));
    }

    private Optional<SyntaxNode> nearest(final String kind, final Optional<SyntaxNode> self) {
      if (self.isPresent() && kind.equals(self.get().kind())) {
        return self;
      }
      for (int index = ancestors.size() - 1; index >= 0; index--) {
        if (kind.equals(ancestors.get(index).kind())) {
          return Optional.of(ancestors.get(index));
        }
      }
      return Optional.empty();
    }

    /** Returns the name of the member that holds the node, or empty when it has none. */
    String memberName() {
      return element.flatMap(this::memberOf).map(this::nameOf).orElse("");
    }

    private Optional<SyntaxNode> memberOf(final SyntaxNode classElement) {
      return classElement.children().stream()
          .filter(child -> MEMBER.equals(child.kind()))
          .findFirst();
    }

    private String nameOf(final SyntaxNode declaration) {
      return declaration.children().stream()
          .filter(child -> PROPERTY_NAME.equals(child.kind()))
          .findFirst()
          .map(SyntaxWalk::text)
          .orElse("");
    }
  }
}
