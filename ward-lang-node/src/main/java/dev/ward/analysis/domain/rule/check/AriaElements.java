package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Whether a JSX element is non-interactive, as Biome 2.5.15's {@code
 * AriaRoles::is_not_interactive_element} decides it (crates/biome_aria/src/roles.rs): the element
 * names with no concept, then the element's implicit role from the mirrored table.
 */
final class AriaElements {

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
}
