package dev.ward.analysis.domain.rule.check;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the mirrored ARIA tables of Biome 2.5.15: the TSV text of each table, without any I/O. The
 * application layer reads the resource files and passes their lines here.
 */
public final class AriaTableParser {

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

  /**
   * Parses the event-handler rows into the events of each group; lines starting with {@code #} and
   * the header are skipped.
   *
   * @param lines the lines of {@code aria-event-handlers.tsv}
   * @throws IllegalArgumentException if a row has the wrong number of columns
   */
  public static Map<String, Set<String>> parseHandlers(final List<String> lines) {
    return data(lines).stream()
        .map(line -> columns(line, 2))
        .collect(
            Collectors.groupingBy(
                row -> row[0],
                HashMap::new,
                Collectors.mapping(row -> row[1], Collectors.toUnmodifiableSet())));
  }

  private AriaTableParser() {}

  /**
   * Parses the attribute table; lines starting with {@code #} and the header are skipped.
   *
   * @param lines the lines of {@code aria-attributes.tsv}
   * @throws IllegalArgumentException if a row has the wrong number of columns
   */
  public static Map<String, AriaRoleTable.AriaAttributeRow> parseAttributes(
      final List<String> lines) {
    return data(lines).stream()
        .map(AriaTableParser::attributeRow)
        .collect(
            Collectors.toMap(
                AriaRoleTable.AriaAttributeRow::name, row -> row, (a, b) -> a, HashMap::new));
  }

  /**
   * Parses the role table; lines starting with {@code #} and the header are skipped.
   *
   * @param lines the lines of {@code aria-roles.tsv}
   * @throws IllegalArgumentException if a row has the wrong number of columns
   */
  public static Map<String, AriaRoleTable.AriaRoleRow> parseRoles(final List<String> lines) {
    return data(lines).stream()
        .map(AriaTableParser::roleRow)
        .collect(
            Collectors.toMap(
                AriaRoleTable.AriaRoleRow::name, row -> row, (a, b) -> a, HashMap::new));
  }

  /**
   * Parses the implicit-role rows; lines starting with {@code #} and the header are skipped.
   *
   * @param lines the lines of {@code aria-implicit-roles.tsv}
   * @throws IllegalArgumentException if a row has the wrong number of columns
   */
  public static List<AriaRoleTable.ImplicitRow> parseImplicit(final List<String> lines) {
    return data(lines).stream().map(AriaTableParser::implicitRow).toList();
  }

  private static AriaRoleTable.AriaRoleRow roleRow(final String line) {
    final String[] c = columns(line, ROLE_COLUMNS);
    return new AriaRoleTable.AriaRoleRow(
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

  private static AriaRoleTable.AriaAttributeRow attributeRow(final String line) {
    final String[] c = columns(line, ATTRIBUTE_COLUMNS);
    return new AriaRoleTable.AriaAttributeRow(
        c[ATTRIBUTE_NAME], c[ATTRIBUTE_VALUE_TYPE], words(c[ATTRIBUTE_VALUES]));
  }

  private static AriaRoleTable.ImplicitRow implicitRow(final String line) {
    final String[] c = columns(line, IMPLICIT_COLUMNS);
    return new AriaRoleTable.ImplicitRow(
        c[ELEMENT], c[CONDITION], "-".equals(c[ROLE]) ? "" : c[ROLE]);
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
        .filter(
            line ->
                !line.startsWith("element\t")
                    && !line.startsWith("attribute\t")
                    && !line.startsWith("group\t"))
        .toList();
  }

  private static Set<String> words(final String text) {
    return text.isBlank()
        ? Set.of()
        : Arrays.stream(text.split(" ")).collect(Collectors.toUnmodifiableSet());
  }
}
