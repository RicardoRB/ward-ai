package dev.ward.analysis.domain.rule.check;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Biome 2.5.15's ARIA role data, read from the mirrored tables under {@code resources/aria}: each
 * role with its classes and attributes, and the ordered implicit roles of the HTML elements. The
 * text is parsed here without I/O; the application layer reads the resource files.
 */
public final class AriaRoleTable {

  private final Map<String, AriaRoleRow> roles;
  private final List<ImplicitRow> implicit;
  private final Map<String, AriaAttributeRow> attributes;
  private final Map<String, Set<String>> handlers;
  private final Map<String, List<ElementConcept>> semantic;

  /**
   * Creates the table from its parsed rows.
   *
   * @param roles the roles by name
   * @param implicit the implicit element-role rows, in order
   * @param attributes the attributes by name
   * @param handlers the event handlers of each group
   * @param semantic the HTML elements of each role, base and related
   */
  public AriaRoleTable(
      final Map<String, AriaRoleRow> roles,
      final List<ImplicitRow> implicit,
      final Map<String, AriaAttributeRow> attributes,
      final Map<String, Set<String>> handlers,
      final Map<String, List<ElementConcept>> semantic) {
    this.roles = Map.copyOf(roles);
    this.implicit = List.copyOf(implicit);
    this.attributes = Map.copyOf(attributes);
    this.handlers = Map.copyOf(handlers);
    this.semantic = Map.copyOf(semantic);
  }

  /** The HTML elements of a role, base and related, with the attributes each one needs. */
  public List<ElementConcept> semanticElements(final String role) {
    return semantic.getOrDefault(role, List.of());
  }

  /** Whether the attribute is an event handler of one of the groups, as Biome's matches does. */
  public boolean isHandler(final List<String> groups, final String attribute) {
    final String lower = attribute.toLowerCase(Locale.ROOT);
    final String event = lower.startsWith("on") ? lower.substring(2) : lower;
    return groups.stream()
        .anyMatch(group -> handlers.getOrDefault(group, Set.of()).contains(event));
  }

  /**
   * The global attributes: the supported attributes of the abstract roletype, as Biome reads them.
   */
  public Set<String> globalAttributes() {
    return role("roletype").map(AriaRoleRow::supported).orElse(Set.of());
  }

  /** Whether the first concrete token names a non-interactive role. */
  public boolean nonInteractiveRole(final List<String> tokens) {
    return tokens.stream()
        .filter(this::isConcrete)
        .findFirst()
        .flatMap(this::role)
        .map(AriaRoleRow::nonInteractive)
        .orElse(false);
  }

  /** The first token that names a concrete role, as Biome's from_roles reads it. */
  public Optional<String> firstConcrete(final List<String> tokens) {
    return tokens.stream().filter(this::isConcrete).findFirst();
  }

  /** The attribute row by name, if the table has the attribute. */
  public Optional<AriaAttributeRow> attribute(final String name) {
    return Optional.ofNullable(attributes.get(name));
  }

  /** Whether the first concrete token names an interactive role, as Biome's from_roles reads it. */
  public boolean interactiveRole(final List<String> tokens) {
    return tokens.stream()
        .filter(this::isConcrete)
        .findFirst()
        .flatMap(this::role)
        .map(AriaRoleRow::interactive)
        .orElse(false);
  }

  /** Whether the name is a concrete (non-abstract) role of Biome's table. */
  public boolean isConcrete(final String name) {
    final AriaRoleRow row = roles.get(name);
    return row != null && !row.isAbstract();
  }

  /** The role row by name, if the table has it. */
  public Optional<AriaRoleRow> role(final String name) {
    return Optional.ofNullable(roles.get(name));
  }

  /**
   * The implicit role of an element, as Biome's {@code get_implicit_role}: the first row for the
   * element whose condition holds. Empty when the element has no implicit role.
   */
  public Optional<String> implicitRole(
      final String element, final Predicate<String> has, final Map<String, String> literals) {
    return implicit.stream()
        .filter(row -> row.element().equals(element))
        .filter(row -> row.condition().holds(has, literals))
        .findFirst()
        .map(ImplicitRow::role)
        .filter(role -> !role.isEmpty());
  }

  /** One role's classes and attributes, as Biome's generated data has them. */
  public record AriaRoleRow(
      String name,
      boolean isAbstract,
      Set<String> superclasses,
      boolean interactive,
      boolean nonInteractive,
      boolean presentational,
      boolean composite,
      Set<String> supported,
      Set<String> required,
      Set<String> prohibited,
      Set<String> deprecated) {

    /** Copies the sets. */
    public AriaRoleRow {
      Objects.requireNonNull(name, "name");
      superclasses = Set.copyOf(superclasses);
      supported = Set.copyOf(supported);
      required = Set.copyOf(required);
      prohibited = Set.copyOf(prohibited);
      deprecated = Set.copyOf(deprecated);
    }
  }

  /** One attribute: its value type, and the token values a token attribute accepts. */
  public record AriaAttributeRow(String name, String valueType, Set<String> values) {

    /** Copies the values. */
    public AriaAttributeRow {
      Objects.requireNonNull(name, "name");
      values = Set.copyOf(values);
    }
  }

  /** One implicit-role row: the element, the condition, and the role ("" for none). */
  public record ImplicitRow(String element, Condition condition, String role) {

    /** Reads the condition from its written form. */
    public ImplicitRow(final String element, final String condition, final String role) {
      this(element, Condition.parse(condition), role);
    }
  }

  /** An HTML element a role maps to, and the static attributes it must have. */
  public record ElementConcept(String element, Map<String, String> attributes) {

    /** Copies the attributes. */
    public ElementConcept {
      attributes = Map.copyOf(attributes);
    }
  }
}
