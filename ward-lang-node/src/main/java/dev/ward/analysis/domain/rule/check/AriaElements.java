package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Whether a JSX element is non-interactive, as Biome 2.5.15's {@code
 * AriaRoles::is_not_interactive_element} decides it (crates/biome_aria/src/roles.rs): the element
 * names with no concept, then the element's implicit role from the mirrored table.
 */
final class AriaElements {

  private static final Set<String> NULL_VALUES = Set.of("{null}", "{undefined}");
  private static final Set<String> STATIC_BY_NAME =
      Set.of(
          "canvas",
          "embed",
          "iframe",
          "video",
          "audio",
          "input",
          "dl",
          "label",
          "legend",
          "ruby",
          "pre",
          "figcaption",
          "br");
  private static final Set<String> NOT_STATIC_BY_NAME =
      Set.of(
          "s",
          "hgroup",
          "meta",
          "base",
          "title",
          "basefont",
          "head",
          "script",
          "noscript",
          "template",
          "style");
  private static final Set<String> NO_CONCEPT =
      Set.of(
          "body",
          "br",
          "details",
          "dir",
          "frame",
          "iframe",
          "label",
          "mark",
          "marquee",
          "menu",
          "meter",
          "optgroup",
          "pre",
          "progress",
          "ruby");

  private AriaElements() {}

  /** The attributes an element has, and the static value of each one that has one. */
  record Attributes(Set<String> present, Map<String, String> literals) {

    static Attributes of(final SyntaxNode element) {
      final Map<String, String> literals = new HashMap<>();
      final Set<String> present =
          JsxAttributes.of(element).stream().map(JsxAttributes::name).collect(Collectors.toSet());
      JsxAttributes.of(element)
          .forEach(
              attribute ->
                  JsxAttributes.literal(attribute)
                      .ifPresent(value -> literals.put(JsxAttributes.name(attribute), value)));
      return new Attributes(present, literals);
    }

    Predicate<String> has() {
      return present::contains;
    }
  }

  /** Tells whether the element named {@code name}, with these attributes, is non-interactive. */
  static boolean isNotInteractive(
      final AriaRoleTable table, final String name, final Attributes attributes) {
    return switch (name) {
      case "header", "a", "area" -> false;
      case "svg" -> true;
      case "input" -> "hidden".equals(attributes.literals().get("type"));
      default ->
          NO_CONCEPT.contains(name)
              || table
                  .implicitRole(name, attributes.has(), attributes.literals())
                  .flatMap(table::role)
                  .map(AriaRoleTable.AriaRoleRow::nonInteractive)
                  .orElse(false);
    };
  }

  /** The role an element has: its first concrete role token, else its implicit role. */
  static Optional<String> roleOf(
      final AriaRoleTable table, final SyntaxNode element, final String source) {
    final Optional<SyntaxNode> role = attributeNamed(element, "role");
    final Optional<String> explicit =
        role.flatMap(attribute -> table.firstConcrete(JsxAttributes.tokens(attribute, source)));
    final Attributes attributes = Attributes.of(element);
    return explicit.or(
        () ->
            table.implicitRole(
                JsxAttributes.elementName(element), attributes.has(), attributes.literals()));
  }

  /** Whether the element is hidden from assistive technology: a truthy aria-hidden or an input. */
  static boolean hidden(final SyntaxNode element) {
    final Attributes attributes = Attributes.of(element);
    final boolean ariaHidden =
        attributes.present().contains("aria-hidden")
            && truthy(attributes.literals().get("aria-hidden"));
    return ariaHidden
        || "input".equals(JsxAttributes.elementName(element))
            && "hidden".equals(attributes.literals().get("type"));
  }

  /** A static value is truthy unless empty or false; a dynamic or missing value is truthy. */
  private static boolean truthy(final String literal) {
    return literal == null || !(literal.isEmpty() || "false".equals(literal));
  }

  /** Whether the element is editable: contentEditable set to the static value true. */
  static boolean contentEditable(final SyntaxNode element) {
    return "true".equals(Attributes.of(element).literals().get("contentEditable"));
  }

  /** Whether the element is a component (a name that is not a plain element name). */
  static boolean component(final SyntaxNode element) {
    return !JsxAttributes.isDomElement(element);
  }

  /** Whether the element name is a custom element: lower case, with a dash. */
  static boolean customElement(final SyntaxNode element) {
    final String name = JsxAttributes.elementName(element);
    return name.contains("-") && name.equals(name.toLowerCase(Locale.ROOT));
  }

  /**
   * Whether the element has an event handler of one of the groups, with a value other than null.
   */
  static boolean hasEventHandler(
      final AriaRoleTable table, final List<String> groups, final SyntaxNode element) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> table.isHandler(groups, JsxAttributes.name(attribute)))
        .anyMatch(
            attribute ->
                !NULL_VALUES.contains(
                    JsxAttributes.value(attribute).map(SyntaxWalk::text).orElse("")));
  }

  /**
   * Whether the element is static, as Biome's no-static-element-interactions reads it: not a "not
   * static" element by name, and by role either absent, presentation or generic.
   */
  static boolean staticElement(
      final AriaRoleTable table, final SyntaxNode element, final String source) {
    final String name = JsxAttributes.elementName(element);
    return !STATIC_BY_NAME.contains(name)
        && (NOT_STATIC_BY_NAME.contains(name)
            || roleOf(table, element, source)
                .map(role -> Set.of("presentation", "generic").contains(role))
                .orElse(true));
  }

  private static Optional<SyntaxNode> attributeNamed(final SyntaxNode element, final String name) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> name.equals(JsxAttributes.name(attribute)))
        .findFirst();
  }
}
