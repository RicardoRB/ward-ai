package dev.ward.infrastructure.cli.rules;

import dev.ward.application.rules.RuleView;
import dev.ward.application.rules.RuleView.OptionView;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.fory.json.annotation.JsonType;
import picocli.CommandLine.Model.CommandSpec;

/** The JSON shapes and the text lines of the {@code ward rules} output. */
final class RulesReport {
  private static final String SEPARATOR = "\t";
  private static final String LIST_SEPARATOR = ", ";

  private RulesReport() {}

  /** The JSON of {@code ward rules list}. */
  @JsonType
  public record RuleListDto(List<RuleDto> rules) {
    /** Copies the rules. */
    public RuleListDto {
      rules = List.copyOf(rules);
    }
  }

  /**
   * One rule in a list.
   *
   * @param id the rule id
   * @param languages the languages the rule covers
   * @param category the category, or an empty text
   * @param presets the presets that select the rule
   * @param description the one-line description
   */
  @JsonType
  public record RuleDto(
      String id,
      List<String> languages,
      String category,
      List<String> presets,
      String description) {
    /** Copies the lists. */
    public RuleDto {
      languages = List.copyOf(languages);
      presets = List.copyOf(presets);
    }
  }

  /**
   * One rule with its options, the JSON of {@code ward rules show}.
   *
   * @param id the rule id
   * @param languages the languages the rule covers
   * @param category the category, or an empty text
   * @param presets the presets that select the rule
   * @param description the one-line description
   * @param options the options the rule declares
   */
  @JsonType
  public record RuleDetailDto(
      String id,
      List<String> languages,
      String category,
      List<String> presets,
      String description,
      List<OptionDto> options) {
    /** Copies the lists. */
    public RuleDetailDto {
      languages = List.copyOf(languages);
      presets = List.copyOf(presets);
      options = List.copyOf(options);
    }
  }

  /**
   * One option. The default is text: a list is joined with commas, and a bound that is not declared
   * is {@code null}.
   *
   * @param name the option name
   * @param type the value type
   * @param defaultValue the default as text, or {@code null}
   * @param min the smallest integer allowed, or {@code null}
   * @param max the largest integer allowed, or {@code null}
   * @param values the allowed texts of an enum
   */
  @JsonType
  public record OptionDto(
      String name, String type, String defaultValue, Long min, Long max, List<String> values) {
    /** Copies the values. */
    public OptionDto {
      values = List.copyOf(values);
    }
  }

  /** The standard output of a command, or the console when the command has no spec. */
  static PrintWriter out(final CommandSpec spec) {
    return spec == null
        ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
        : spec.commandLine().getOut();
  }

  /** The standard error of a command, or the console when the command has no spec. */
  static PrintWriter error(final CommandSpec spec) {
    return spec == null
        ? new PrintWriter(System.err, true, StandardCharsets.UTF_8)
        : spec.commandLine().getErr();
  }

  /** Returns the JSON shape of one rule in a list. */
  static RuleDto rule(final RuleView view) {
    return new RuleDto(
        view.id(), view.languages(), view.category(), view.presets(), view.description());
  }

  /** Returns the JSON shape of one rule with its options. */
  static RuleDetailDto detail(final RuleView view) {
    return new RuleDetailDto(
        view.id(),
        view.languages(),
        view.category(),
        view.presets(),
        view.description(),
        view.options().stream().map(RulesReport::option).toList());
  }

  /** Returns one tab-separated line per rule: id, languages, category, presets, description. */
  static String line(final RuleView view) {
    return String.join(
        SEPARATOR,
        view.id(),
        String.join(LIST_SEPARATOR, view.languages()),
        view.category(),
        String.join(LIST_SEPARATOR, view.presets()),
        view.description());
  }

  /** Returns the text block of {@code ward rules show}. */
  static List<String> details(final RuleView view) {
    final List<String> lines =
        List.of(
            view.id(),
            "  description: " + view.description(),
            "  languages: " + String.join(LIST_SEPARATOR, view.languages()),
            "  category: " + view.category(),
            "  presets: " + presetsText(view));
    if (view.options().isEmpty()) {
      return lines;
    }
    final List<String> result = new ArrayList<>(lines);
    result.add("  options:");
    view.options().forEach(option -> result.add("    " + optionLine(option)));
    return result;
  }

  private static String presetsText(final RuleView view) {
    return view.presets().isEmpty()
        ? "none (include only)"
        : String.join(LIST_SEPARATOR, view.presets());
  }

  private static String optionLine(final OptionView option) {
    final StringBuilder line = new StringBuilder(option.name()).append("  ").append(option.type());
    if (option.defaultValue() != null) {
      line.append("  default ").append(defaultText(option.defaultValue()));
    }
    if (option.min() != null) {
      line.append("  min ").append(option.min());
    }
    if (option.max() != null) {
      line.append("  max ").append(option.max());
    }
    if (!option.values().isEmpty()) {
      line.append("  values ").append(String.join(LIST_SEPARATOR, option.values()));
    }
    return line.toString();
  }

  private static OptionDto option(final OptionView option) {
    return new OptionDto(
        option.name(),
        option.type(),
        option.defaultValue() == null ? null : defaultText(option.defaultValue()),
        option.min(),
        option.max(),
        option.values());
  }

  private static String defaultText(final Object value) {
    if (value instanceof List<?> list) {
      return list.stream().map(String::valueOf).collect(Collectors.joining(LIST_SEPARATOR));
    }
    return String.valueOf(value);
  }
}
