package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Biome 2.5.15's ARIA role data, read from the mirrored tables under {@code resources/aria}: each
 * role with its classes and attributes, and the ordered implicit roles of the HTML elements. The
 * text is parsed here without I/O; the application layer reads the resource files.
 */
public final class AriaRoleTable {

  private final Map<String, AriaRoleRow> roles;
  private final List<ImplicitRow> implicit;

  /**
   * Creates the table from its parsed rows.
   *
   * @param roles the roles by name
   * @param implicit the implicit element-role rows, in order
   */
  public AriaRoleTable(final Map<String, AriaRoleRow> roles, final List<ImplicitRow> implicit) {
    this.roles = Map.copyOf(roles);
    this.implicit = List.copyOf(implicit);
  }

  /**
   * Parses the role table; lines starting with {@code #} and the header are skipped.
   *
   * @param lines the lines of {@code aria-roles.tsv}
   * @throws IllegalArgumentException if a row has the wrong number of columns
   */
  public static Map<String, AriaRoleRow> parseRoles(final List<String> lines) {
    final Map<String, AriaRoleRow> roles = new java.util.HashMap<>();
    for (final String line : data(lines)) {
      final String[] c = line.split("\t", -1);
      if (c.length != 11) {
        throw new IllegalArgumentException("role row needs 11 columns: " + line);
      }
      roles.put(
          c[0],
          new AriaRoleRow(
              c[0],
              Boolean.parseBoolean(c[1]),
              words(c[2]),
              Boolean.parseBoolean(c[3]),
              Boolean.parseBoolean(c[4]),
              Boolean.parseBoolean(c[5]),
              Boolean.parseBoolean(c[6]),
              words(c[7]),
              words(c[8]),
              words(c[9]),
              words(c[10])));
    }
    return roles;
  }

  /**
   * Parses the implicit-role rows; lines starting with {@code #} and the header are skipped.
   *
   * @param lines the lines of {@code aria-implicit-roles.tsv}
   * @throws IllegalArgumentException if a row has the wrong number of columns
   */
  public static List<ImplicitRow> parseImplicit(final List<String> lines) {
    final List<ImplicitRow> rows = new ArrayList<>();
    for (final String line : data(lines)) {
      final String[] c = line.split("\t", -1);
      if (c.length != 3) {
        throw new IllegalArgumentException("implicit row needs 3 columns: " + line);
      }
      rows.add(new ImplicitRow(c[0], c[1], "-".equals(c[2]) ? "" : c[2]));
    }
    return rows;
  }

  /** Whether the name is a concrete (non-abstract) role of Biome's table. */
  public boolean isConcrete(final String name) {
    final AriaRoleRow row = roles.get(name);
    return row != null && !row.isAbstract();
  }

  /** The role row by name, if the table has it. */
  public java.util.Optional<AriaRoleRow> role(final String name) {
    return java.util.Optional.ofNullable(roles.get(name));
  }

  /**
   * The implicit role of an element, as Biome's {@code get_implicit_role}: the first row for the
   * element whose condition holds. Empty when the element has no implicit role.
   */
  public java.util.Optional<String> implicitRole(
      final String element, final Predicate<String> has, final Map<String, String> literals) {
    return implicit.stream()
        .filter(row -> row.element().equals(element))
        .filter(row -> row.condition().holds(has, literals))
        .findFirst()
        .map(ImplicitRow::role)
        .filter(role -> !role.isEmpty());
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
        : java.util.Arrays.stream(text.split(" ")).collect(Collectors.toUnmodifiableSet());
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

  /** One implicit-role row: the element, the condition, and the role ("" for none). */
  public record ImplicitRow(String element, Condition condition, String role) {

    /** Reads the condition from its written form. */
    public ImplicitRow(final String element, final String condition, final String role) {
      this(element, Condition.parse(condition), role);
    }
  }
}
