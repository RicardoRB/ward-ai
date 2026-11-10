package dev.ward.application.rules;

import java.util.List;

/**
 * One rule as the {@code ward rules} commands show it.
 *
 * @param id the rule id, {@code ward:<language>/<rule>}
 * @param languages the languages the rule covers, in lower case, sorted
 * @param category the group of the rule, or empty
 * @param presets the presets that select the rule, in lower case, in preset order
 * @param description the one-line message of the rule, or its id when the rule has none
 * @param options the options the rule declares, in declaration order
 */
public record RuleView(
    String id,
    List<String> languages,
    String category,
    List<String> presets,
    String description,
    List<OptionView> options) {

  /** Copies the lists. */
  public RuleView {
    languages = List.copyOf(languages);
    presets = List.copyOf(presets);
    options = List.copyOf(options);
  }

  /**
   * One option of a rule.
   *
   * @param name the option name in {@code ward-rules.toml}
   * @param type the value type, in lower case with dashes, such as {@code integer}
   * @param defaultValue the value the rule body has by default, or {@code null}
   * @param min the smallest integer allowed, or {@code null} when unbounded
   * @param max the largest integer allowed, or {@code null} when unbounded
   * @param values the allowed texts of an enum, empty for other types
   */
  public record OptionView(
      String name, String type, Object defaultValue, Long min, Long max, List<String> values) {

    /** Copies the values. */
    public OptionView {
      values = List.copyOf(values);
    }
  }
}
