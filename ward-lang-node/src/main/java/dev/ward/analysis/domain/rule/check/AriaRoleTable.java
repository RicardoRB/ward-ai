package dev.ward.analysis.domain.rule.check;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Biome 2.5.15's ARIA role data, read from the mirrored tables under {@code resources/aria}: each
 * role with its classes and attributes, and the ordered implicit roles of the HTML elements. The
 * text is parsed here without I/O; the application layer reads the resource files.
 */
public final class AriaRoleTable {

  private static final int ROLE_COLUMNS = 11;
  private static final int NAME = 0;
  private static final int ABSTRACT = 1;
  private static final int SUPERCLASSES = 2;
  private static final int INTERACTIVE = 3;
  private static final int NON_INTERACTIVE = 4;
  private static final int PRESENTATIONAL = 5;
  private static final int COMPOSITE = 6;
  private static final int SUPPORTED = 7;
  private static final int REQUIRED = 8;
  private static final int PROHIBITED = 9;
  private static final int DEPRECATED = 10;
  private static final int IMPLICIT_COLUMNS = 3;
  private static final int ELEMENT = 0;
  private static final int CONDITION = 1;
  private static final int ROLE = 2;

  private static final int ATTRIBUTE_COLUMNS = 5;
  private static final int ATTRIBUTE_NAME = 0;
  private static final int ATTRIBUTE_VALUE_TYPE = 2;
  private static final int ATTRIBUTE_VALUES = 3;

  private final Map<String, AriaRoleRow> roles;
  private final List<ImplicitRow> implicit;
  private final Map<String, AriaAttributeRow> attributes;

  /**
   * Creates the table from its parsed rows.
   *
   * @param roles the roles by name
   * @param implicit the implicit element-role rows, in order
   * @param attributes the attributes by name
   */
  public AriaRoleTable(
      final Map<String, AriaRoleRow> roles,
      final List<ImplicitRow> implicit,
      final Map<String, AriaAttributeRow> attributes) {
    this.roles = Map.copyOf(roles);
    this.implicit = List.copyOf(implicit);
    this.attributes = Map.copyOf(attributes);
  }

  /**
   * Parses the attribute table; lines starting with {@code #} and the header are skipped.
   *
   * @param lines the lines of {@code aria-attributes.tsv}
   * @throws IllegalArgumentException if a row has the wrong number of columns
   */
  public static Map<String, AriaAttributeRow> parseAttributes(final List<String> lines) {
    return data(lines).stream()
        .map(AriaRoleTable::attributeRow)
        .collect(Collectors.toMap(AriaAttributeRow::name, row -> row, (a, b) -> a, HashMap::new));
  }

  /** The attribute row by name, if the table has the attribute. */
  public Optional<AriaAttributeRow> attribute(final String name) {
    return Optional.ofNullable(attributes.get(name));
  }

  /**
   * Parses the role table; lines starting with {@code #} and the header are skipped.
   *
   * @param lines the lines of {@code aria-roles.tsv}
   * @throws IllegalArgumentException if a row has the wrong number of columns
   */
  public static Map<String, AriaRoleRow> parseRoles(final List<String> lines) {
    return data(lines).stream()
        .map(AriaRoleTable::roleRow)
        .collect(Collectors.toMap(AriaRoleRow::name, row -> row, (a, b) -> a, HashMap::new));
  }

  /**
   * Parses the implicit-role rows; lines starting with {@code #} and the header are skipped.
   *
   * @param lines the lines of {@code aria-implicit-roles.tsv}
   * @throws IllegalArgumentException if a row has the wrong number of columns
   */
  public static List<ImplicitRow> parseImplicit(final List<String> lines) {
    return data(lines).stream().map(AriaRoleTable::implicitRow).toList();
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

  private static AriaRoleRow roleRow(final String line) {
    final String[] c = columns(line, ROLE_COLUMNS);
    return new AriaRoleRow(
        c[NAME],
        Boolean.parseBoolean(c[ABSTRACT]),
        words(c[SUPERCLASSES]),
        Boolean.parseBoolean(c[INTERACTIVE]),
        Boolean.parseBoolean(c[NON_INTERACTIVE]),
        Boolean.parseBoolean(c[PRESENTATIONAL]),
        Boolean.parseBoolean(c[COMPOSITE]),
        words(c[SUPPORTED]),
        words(c[REQUIRED]),
        words(c[PROHIBITED]),
        words(c[DEPRECATED]));
  }

  private static AriaAttributeRow attributeRow(final String line) {
    final String[] c = columns(line, ATTRIBUTE_COLUMNS);
    return new AriaAttributeRow(
        c[ATTRIBUTE_NAME], c[ATTRIBUTE_VALUE_TYPE], words(c[ATTRIBUTE_VALUES]));
  }

  private static ImplicitRow implicitRow(final String line) {
    final String[] c = columns(line, IMPLICIT_COLUMNS);
    return new ImplicitRow(c[ELEMENT], c[CONDITION], "-".equals(c[ROLE]) ? "" : c[ROLE]);
  }

  private static String[] columns(final String line, final int expected) {
    final String[] c = line.split("\t", -1);
    if (c.length != expected) {
      throw new IllegalArgumentException(expected + " columns expected: " + line);
    }
    return c;
  }

  private static List<String> data(final List<String> lines) {
    return lines.stream()
        .filter(line -> !line.isBlank() && !line.startsWith("#") && !line.startsWith("role\t"))
        .filter(line -> !line.startsWith("element\t") && !line.startsWith("attribute\t"))
        .toList();
  }

  private static Set<String> words(final String text) {
    return text.isBlank()
        ? Set.of()
        : Arrays.stream(text.split(" ")).collect(Collectors.toUnmodifiableSet());
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
}
