package dev.ward.application.gate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What {@code ward-rules.toml} says, before it is checked against the rules that exist. The reader
 * has already checked the shape of the file; {@link RuleConfigResolver} checks the rules, options
 * and values.
 *
 * @param name the project-relative path of the file, used to name it in every problem
 * @param select the {@code [select]} table
 * @param rules the rule tables by rule id, each a map from key to value; a value is a {@link Long},
 *     {@link Boolean}, {@link String}, or a {@link List} of them
 * @param headers the table header of each rule as the user wrote it, by rule id, such as {@code
 *     [rules.java.method-length]}; a missing id uses the quoted form
 * @param problems the shape problems the reader found, each already naming the file, table and key
 */
public record RuleConfigFile(
    String name,
    Select select,
    Map<String, Map<String, Object>> rules,
    Map<String, String> headers,
    List<String> problems) {

  /** The file name {@code ward-rules.toml} at the project root, used when no path is configured. */
  public static final String DEFAULT_NAME = "ward-rules.toml";

  /** Validates and copies the parts; the tables keep their order. */
  public RuleConfigFile {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(select, "select");
    rules = copyTables(rules);
    headers = Map.copyOf(headers);
    problems = List.copyOf(problems);
  }

  /** A file whose rule tables all use the quoted header form. */
  public RuleConfigFile(
      final String name,
      final Select select,
      final Map<String, Map<String, Object>> rules,
      final List<String> problems) {
    this(name, select, rules, Map.of(), problems);
  }

  /** Returns a file that only reports {@code problem}. */
  public static RuleConfigFile unusable(final String name, final String problem) {
    return new RuleConfigFile(name, Select.EMPTY, Map.of(), List.of(name + ": " + problem));
  }

  /**
   * Returns the table header of the rule {@code id}, such as {@code [rules.java.method-length]}.
   */
  public String header(final String id) {
    return headers.getOrDefault(id, "[rules.\"" + id + "\"]");
  }

  /** Returns a copy of the rule tables, in file order. */
  public Map<String, Map<String, Object>> rules() {
    return copyTables(rules);
  }

  private static Map<String, Map<String, Object>> copyTables(
      final Map<String, Map<String, Object>> tables) {
    final Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
    tables.forEach(
        (id, table) -> copy.put(id, Collections.unmodifiableMap(new LinkedHashMap<>(table))));
    return Collections.unmodifiableMap(copy);
  }

  /**
   * The {@code [select]} table.
   *
   * @param preset {@code none}, {@code standard} or {@code strict}; {@code null} for the default
   *     {@code standard}
   * @param languages the languages to keep, or empty for every language
   * @param categories the categories to keep, or empty for every category
   * @param include the rule ids to add
   * @param exclude the rule ids to remove
   */
  public record Select(
      String preset,
      List<String> languages,
      List<String> categories,
      List<String> include,
      List<String> exclude) {

    /** A table that sets nothing. */
    public static final Select EMPTY = new Select(null, List.of(), List.of(), List.of(), List.of());

    /** Copies the lists. */
    public Select {
      languages = List.copyOf(languages);
      categories = List.copyOf(categories);
      include = List.copyOf(include);
      exclude = List.copyOf(exclude);
    }
  }
}
