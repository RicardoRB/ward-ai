package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.CognitiveComplexityCheck;
import dev.ward.analysis.domain.rule.check.DeclarationFilter;
import dev.ward.analysis.domain.rule.check.DescendantCountCheck;
import dev.ward.analysis.domain.rule.check.LinesCheck;
import dev.ward.analysis.domain.rule.check.PrecedingCommentCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.TextPatternCheck;
import dev.ward.analysis.domain.rule.check.UnreferencedNameCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseResult;

/**
 * Builds the {@link RuleCheck} that a rule body describes. A body is TOML: {@code check} names one
 * check, and the other keys are that check's parameters. Unknown checks and missing or unusable
 * parameters are configuration errors.
 */
final class DeclarativeChecks {

  private static final String CHECK = "check";
  private static final String KINDS = "kinds";
  private static final String MAX = "max";
  private static final String MESSAGE = "message";
  private static final String MODIFIERS = "modifiers";
  private static final String REQUIRED = "required";
  private static final String EXCLUDED = "excluded";

  private DeclarativeChecks() {}

  /**
   * Builds the check of {@code body}.
   *
   * @throws RuleConfigurationException if the body is not valid TOML, names an unknown check, or
   *     has a missing or unusable parameter
   */
  static RuleCheck build(final String body) {
    final TomlParseResult toml = Toml.parse(body);
    if (toml.hasErrors()) {
      throw new RuleConfigurationException("the rule body is not valid TOML");
    }
    final String name = required(toml, CHECK);
    try {
      return create(name, toml);
    } catch (final IllegalArgumentException exception) {
      throw new RuleConfigurationException(
          "check '" + name + "' cannot be used: " + exception.getMessage(), exception);
    } catch (final TomlInvalidTypeException exception) {
      throw new RuleConfigurationException(
          "check '" + name + "' has a parameter of the wrong type", exception);
    }
  }

  private static RuleCheck create(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "lines" ->
          new LinesCheck(strings(toml, KINDS), integer(toml, MAX), required(toml, MESSAGE));
      case "descendant-count" -> descendantCount(toml);
      case "cognitive-complexity" -> cognitiveComplexity(toml);
      case "text-pattern" -> textPattern(toml);
      case "unreferenced-name" -> new UnreferencedNameCheck(required(toml, MESSAGE));
      case "preceding-comment" -> precedingComment(toml);
      default -> throw new RuleConfigurationException("unknown check '" + name + "'");
    };
  }

  private static RuleCheck descendantCount(final TomlParseResult toml) {
    return new DescendantCountCheck(
        strings(toml, KINDS),
        strings(toml, "counted"),
        optionalInteger(toml, "base", 0),
        integer(toml, MAX),
        required(toml, MESSAGE));
  }

  private static RuleCheck cognitiveComplexity(final TomlParseResult toml) {
    return new CognitiveComplexityCheck(
        strings(toml, KINDS),
        strings(toml, "structures"),
        strings(toml, "flat"),
        integer(toml, MAX),
        required(toml, MESSAGE));
  }

  private static RuleCheck textPattern(final TomlParseResult toml) {
    return new TextPatternCheck(
        filter(toml),
        required(toml, "name"),
        optionalStrings(toml, "skip"),
        required(toml, "pattern"),
        required(toml, MESSAGE));
  }

  private static RuleCheck precedingComment(final TomlParseResult toml) {
    return new PrecedingCommentCheck(
        filter(toml),
        Optional.ofNullable(toml.getString("exempt-annotation")),
        required(toml, MESSAGE));
  }

  private static DeclarationFilter filter(final TomlParseResult toml) {
    return new DeclarationFilter(
        strings(toml, KINDS),
        strings(toml, MODIFIERS),
        Optional.ofNullable(toml.getString(REQUIRED)),
        Optional.ofNullable(toml.getString(EXCLUDED)));
  }

  private static String required(final TomlParseResult toml, final String key) {
    final String value = toml.getString(key);
    if (value == null) {
      throw new RuleConfigurationException("'" + key + "' must be a string");
    }
    return value;
  }

  private static int integer(final TomlParseResult toml, final String key) {
    final Long value = toml.getLong(key);
    if (value == null) {
      throw new RuleConfigurationException("'" + key + "' must be an integer");
    }
    return toInt(key, value);
  }

  private static int optionalInteger(
      final TomlParseResult toml, final String key, final int fallback) {
    return toml.contains(key) ? integer(toml, key) : fallback;
  }

  private static int toInt(final String key, final long value) {
    if (value < 0 || value > Integer.MAX_VALUE) {
      throw new RuleConfigurationException("'" + key + "' is out of range");
    }
    return (int) value;
  }

  private static Set<String> strings(final TomlParseResult toml, final String key) {
    if (!toml.contains(key)) {
      throw new RuleConfigurationException("'" + key + "' must be a list of strings");
    }
    return stringList(toml.getArray(key), key);
  }

  private static Set<String> optionalStrings(final TomlParseResult toml, final String key) {
    return toml.contains(key) ? strings(toml, key) : Set.of();
  }

  private static Set<String> stringList(final TomlArray array, final String key) {
    if (array == null) {
      throw new RuleConfigurationException("'" + key + "' must be a list of strings");
    }
    final List<String> values = new ArrayList<>();
    for (int index = 0; index < array.size(); index++) {
      values.add(stringAt(array, index, key));
    }
    return Set.copyOf(values);
  }

  private static String stringAt(final TomlArray array, final int index, final String key) {
    try {
      final String value = array.getString(index);
      if (value != null) {
        return value;
      }
    } catch (final TomlInvalidTypeException exception) {
      throw new RuleConfigurationException("'" + key + "' must hold only strings", exception);
    }
    throw new RuleConfigurationException("'" + key + "' must hold only strings");
  }
}
