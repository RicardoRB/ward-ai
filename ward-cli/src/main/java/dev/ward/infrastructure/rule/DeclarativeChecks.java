package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.CognitiveComplexityCheck;
import dev.ward.analysis.domain.rule.check.CognitiveTokens;
import dev.ward.analysis.domain.rule.check.CountedKinds;
import dev.ward.analysis.domain.rule.check.DeclarationFilter;
import dev.ward.analysis.domain.rule.check.DescendantCountCheck;
import dev.ward.analysis.domain.rule.check.LinesCheck;
import dev.ward.analysis.domain.rule.check.PrecedingCommentCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.TextPatternCheck;
import dev.ward.analysis.domain.rule.check.UnreferencedNameCheck;
import java.util.Optional;
import org.tomlj.Toml;
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
    final String name = TomlValues.required(toml, CHECK);
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
          new LinesCheck(
              TomlValues.strings(toml, KINDS),
              TomlValues.integer(toml, MAX),
              TomlValues.required(toml, MESSAGE));
      case "descendant-count" -> descendantCount(toml);
      case "cognitive-complexity" -> cognitiveComplexity(toml);
      case "text-pattern" -> textPattern(toml);
      case "unreferenced-name" -> new UnreferencedNameCheck(TomlValues.required(toml, MESSAGE));
      case "preceding-comment" -> precedingComment(toml);
      default -> throw new RuleConfigurationException("unknown check '" + name + "'");
    };
  }

  private static RuleCheck descendantCount(final TomlParseResult toml) {
    return new DescendantCountCheck(
        TomlValues.strings(toml, KINDS),
        countedKinds(toml, "counted"),
        TomlValues.optionalInteger(toml, "base", 0),
        TomlValues.integer(toml, MAX),
        TomlValues.required(toml, MESSAGE));
  }

  private static CountedKinds countedKinds(final TomlParseResult toml, final String key) {
    return new CountedKinds(
        TomlValues.strings(toml, key),
        TomlValues.kindMap(toml, "requires-sibling"),
        TomlValues.kindMap(toml, "cancelled-by"));
  }

  private static RuleCheck cognitiveComplexity(final TomlParseResult toml) {
    return new CognitiveComplexityCheck(
        TomlValues.strings(toml, KINDS),
        new CognitiveTokens(
            countedKinds(toml, "structures"),
            TomlValues.strings(toml, "flat"),
            TomlValues.optionalStrings(toml, "chaining")),
        TomlValues.integer(toml, MAX),
        TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck textPattern(final TomlParseResult toml) {
    return new TextPatternCheck(
        filter(toml),
        TomlValues.required(toml, "name"),
        TomlValues.optionalStrings(toml, "skip"),
        TomlValues.required(toml, "pattern"),
        TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck precedingComment(final TomlParseResult toml) {
    return new PrecedingCommentCheck(
        filter(toml),
        Optional.ofNullable(toml.getString("exempt-annotation")),
        TomlValues.required(toml, MESSAGE));
  }

  private static DeclarationFilter filter(final TomlParseResult toml) {
    return new DeclarationFilter(
        TomlValues.strings(toml, KINDS),
        TomlValues.strings(toml, MODIFIERS),
        Optional.ofNullable(toml.getString(REQUIRED)),
        Optional.ofNullable(toml.getString(EXCLUDED)));
  }
}
