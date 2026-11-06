package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.RuleOptionType;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.tomlj.TomlParseResult;

/**
 * The generic check kinds a rule body selects with {@code check = "<id>"}: each kind's parameters
 * and which of them are user options. Every other parameter is fixed by the rule author.
 */
enum GenericKind {
  IDENTIFIER_PATTERN(
      "identifier-pattern",
      List.of("name", "skip", "pattern"),
      List.of(option("pattern", RuleOptionType.REGEX))),
  FORBIDDEN_NODE(
      "forbidden-node",
      List.of("text", "with-child", "parent"),
      List.of(option("text", RuleOptionType.REGEX))),
  REQUIRED_NODE("required-node", List.of("child", "descendant", "skip", "sibling"), List.of()),
  COUNT("count", List.of("counted", "skip", "max"), List.of(option("max", RuleOptionType.INTEGER))),
  DEPTH("depth", List.of("reset", "max"), List.of(option("max", RuleOptionType.INTEGER))),
  TEXT_PATTERN(
      "text-pattern",
      List.of("target", "pattern", "required", "limit", "header-lines"),
      List.of(option("pattern", RuleOptionType.REGEX), option("limit", RuleOptionType.INTEGER))),
  ORDER(
      "order",
      List.of("order", "group-by", "group-skip"),
      List.of(option("order", RuleOptionType.STRING_LIST))),
  LITERAL(
      "literal",
      List.of("ignore", "ignore-contexts", "max-repeats"),
      List.of(
          option("ignore", RuleOptionType.STRING_LIST),
          option("ignore-contexts", RuleOptionType.STRING_LIST),
          option("max-repeats", RuleOptionType.INTEGER))),
  DUPLICATE("duplicate", List.of("items", "key"), List.of());

  /** The keys every generic kind accepts: the node kinds, the shared filters and the message. */
  private static final List<String> SHARED =
      List.of(
          "check",
          "kinds",
          "message",
          "modifiers",
          "not-modifiers",
          "inside",
          "not-inside",
          "sources");

  private final String id;
  private final List<String> parameters;
  private final List<Option> options;

  GenericKind(final String id, final List<String> parameters, final List<Option> options) {
    this.id = id;
    this.parameters = parameters;
    this.options = options;
  }

  /** The name and type of a user option. */
  record Option(String name, RuleOptionType type) {}

  private static Option option(final String name, final RuleOptionType type) {
    return new Option(name, type);
  }

  /** Finds the kind with this id; the legacy checks, such as {@code lines}, have none. */
  static Optional<GenericKind> find(final String id) {
    return Stream.of(values()).filter(kind -> kind.id.equals(id)).findFirst();
  }

  /**
   * Finds the generic kind that {@code toml} selects with {@code check = name}. The legacy {@code
   * text-pattern} check shares a name with the generic one; the generic one has a {@code target}.
   */
  static Optional<GenericKind> select(final String name, final TomlParseResult toml) {
    return find(name).filter(kind -> kind != TEXT_PATTERN || toml.contains("target"));
  }

  String id() {
    return id;
  }

  List<Option> options() {
    return options;
  }

  /** Returns the keys of {@code present} that this kind does not accept, sorted. */
  List<String> unknown(final Set<String> present) {
    final Set<String> unknown = new HashSet<>(present);
    unknown.removeAll(SHARED);
    unknown.removeAll(parameters);
    return unknown.stream().sorted().toList();
  }
}
