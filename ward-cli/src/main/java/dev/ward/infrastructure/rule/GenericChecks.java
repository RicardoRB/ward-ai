package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.TomlLists;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.CountCheck;
import dev.ward.analysis.domain.rule.check.DepthCheck;
import dev.ward.analysis.domain.rule.check.DuplicateCheck;
import dev.ward.analysis.domain.rule.check.ForbiddenNodeCheck;
import dev.ward.analysis.domain.rule.check.IdentifierPatternCheck;
import dev.ward.analysis.domain.rule.check.LiteralCheck;
import dev.ward.analysis.domain.rule.check.ModifierTable;
import dev.ward.analysis.domain.rule.check.NodeFilter;
import dev.ward.analysis.domain.rule.check.OrderCheck;
import dev.ward.analysis.domain.rule.check.RequiredNodeCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.Sources;
import dev.ward.analysis.domain.rule.check.TextMatchCheck;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/**
 * Builds the generic checks, selected by {@code check = "<kind>"}, and lists their user options. An
 * unknown parameter is a configuration error that names the rule's kind.
 */
final class GenericChecks {

  private static final String MESSAGE = "message";
  private static final String MAX = "max";
  private static final String PATTERN = "pattern";
  private static final String SKIP = "skip";

  private GenericChecks() {}

  /** Builds the check of {@code kind} from the body, reading modifiers through {@code table}. */
  static RuleCheck build(
      final GenericKind kind, final TomlParseResult toml, final ModifierTable table) {
    final List<String> unknown = kind.unknown(toml.keySet());
    if (!unknown.isEmpty()) {
      throw new RuleConfigurationException(
          "check '" + kind.id() + "' does not accept " + String.join(", ", unknown));
    }
    return create(kind, toml, filter(toml, table), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck create(
      final GenericKind kind,
      final TomlParseResult toml,
      final NodeFilter filter,
      final String message) {
    return switch (kind) {
      case IDENTIFIER_PATTERN ->
          new IdentifierPatternCheck(
              filter,
              TomlValues.required(toml, "name"),
              TomlValues.optionalStrings(toml, SKIP),
              TomlValues.required(toml, PATTERN),
              message);
      case FORBIDDEN_NODE -> new ForbiddenNodeCheck(filter, condition(toml), message);
      case REQUIRED_NODE -> new RequiredNodeCheck(filter, requirement(toml), message);
      case ORDER -> order(filter, toml, message);
      case DUPLICATE ->
          new DuplicateCheck(
              filter,
              TomlValues.strings(toml, "items"),
              Optional.ofNullable(toml.getString("key")),
              message);
      default -> measured(kind, toml, filter, message);
    };
  }

  /** The kinds that measure: counts, depth, text and literals. */
  private static RuleCheck measured(
      final GenericKind kind,
      final TomlParseResult toml,
      final NodeFilter filter,
      final String message) {
    return switch (kind) {
      case COUNT ->
          new CountCheck(
              filter,
              TomlValues.strings(toml, "counted"),
              TomlValues.optionalStrings(toml, SKIP),
              TomlValues.integer(toml, MAX),
              message);
      case DEPTH ->
          new DepthCheck(
              filter,
              TomlValues.optionalStrings(toml, "reset"),
              TomlValues.integer(toml, MAX),
              message);
      case TEXT_PATTERN -> new TextMatchCheck(filter, textRule(toml), message);
      case LITERAL ->
          new LiteralCheck(
              filter,
              TomlValues.optionalStrings(toml, "ignore"),
              TomlValues.optionalStrings(toml, "ignore-contexts"),
              TomlValues.optionalInteger(toml, "max-repeats", LiteralCheck.NO_REPEAT_LIMIT),
              message);
      default -> throw new RuleConfigurationException("unknown check '" + kind.id() + "'");
    };
  }

  private static OrderCheck order(
      final NodeFilter filter, final TomlParseResult toml, final String message) {
    return new OrderCheck(
        filter,
        TomlLists.optional(toml, "order"),
        Optional.ofNullable(toml.getString("group-by"))
            .map(
                name ->
                    new OrderCheck.GroupBy(name, TomlValues.optionalStrings(toml, "group-skip"))),
        message);
  }

  private static NodeFilter filter(final TomlParseResult toml, final ModifierTable table) {
    return new NodeFilter(
        TomlValues.optionalStrings(toml, "kinds"),
        table,
        TomlValues.optionalStrings(toml, "modifiers"),
        TomlValues.optionalStrings(toml, "not-modifiers"),
        TomlValues.optionalStrings(toml, "inside"),
        TomlValues.optionalStrings(toml, "not-inside"),
        Sources.parse(toml.getString("sources", () -> "all")));
  }

  private static ForbiddenNodeCheck.Condition condition(final TomlParseResult toml) {
    return new ForbiddenNodeCheck.Condition(
        Optional.ofNullable(toml.getString("text")).map(Pattern::compile),
        TomlValues.optionalStrings(toml, "with-child"),
        TomlValues.optionalStrings(toml, "parent"));
  }

  private static RequiredNodeCheck.Requirement requirement(final TomlParseResult toml) {
    return new RequiredNodeCheck.Requirement(
        TomlValues.optionalStrings(toml, "child"),
        TomlValues.optionalStrings(toml, "descendant"),
        TomlValues.optionalStrings(toml, SKIP),
        TomlValues.optionalStrings(toml, "sibling"));
  }

  private static TextMatchCheck.Rule textRule(final TomlParseResult toml) {
    final boolean required = toml.getBoolean("required", () -> false);
    return new TextMatchCheck.Rule(
        target(TomlValues.required(toml, "target")),
        Pattern.compile(TomlValues.required(toml, PATTERN)),
        required,
        TomlValues.optionalInteger(toml, "limit", required ? 1 : 0),
        TomlValues.optionalInteger(toml, "header-lines", 1));
  }

  private static TextMatchCheck.Target target(final String value) {
    return switch (value) {
      case "lines" -> TextMatchCheck.Target.LINES;
      case "comments" -> TextMatchCheck.Target.COMMENTS;
      case "strings" -> TextMatchCheck.Target.STRINGS;
      case "filename" -> TextMatchCheck.Target.FILENAME;
      case "header" -> TextMatchCheck.Target.HEADER;
      default ->
          throw new RuleConfigurationException(
              "target must be lines, comments, strings, filename or header");
    };
  }
}
