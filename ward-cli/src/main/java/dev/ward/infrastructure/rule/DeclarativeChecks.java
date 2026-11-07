package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.Optional;
import java.util.Set;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseResult;

/**
 * Builds the {@link RuleCheck} that a rule body describes. A body is TOML: {@code check} names one
 * check, and the other keys are that check's parameters. Unknown checks and missing or unusable
 * parameters are configuration errors.
 */
final class DeclarativeChecks {

  private final CheckRegistry registry;

  private static final String CHECK = "check";
  private static final String KINDS = "kinds";
  private static final String MAX = "max";
  private static final String MESSAGE = "message";
  private static final String MODIFIERS = "modifiers";
  private static final String REQUIRED = "required";
  private static final String EXCLUDED = "excluded";
  private static final String SPECS = "specs";
  private static final String COUNT_EMPTY = "count-empty";

  /** Creates the builder over the checks of {@code registry}. */
  DeclarativeChecks(final CheckRegistry registry) {
    this.registry = registry;
  }

  /** Creates the builder over the checks of every bundled language module. */
  static DeclarativeChecks bundled() {
    return new DeclarativeChecks(BundledLanguages.checkRegistry());
  }

  /**
   * Builds the check of {@code body}.
   *
   * @throws RuleConfigurationException if the body is not valid TOML, names an unknown check, or
   *     has a missing or unusable parameter
   */
  RuleCheck build(final String body) {
    return build(body, Set.of());
  }

  /**
   * Builds the check of {@code body} for a rule that covers {@code languages}. A generic check
   * reads its modifier names through the table of the language when the rule covers just one.
   *
   * @throws RuleConfigurationException as {@link #build(String)}
   */
  RuleCheck build(final String body, final Set<AnalysisLanguage> languages) {
    final TomlParseResult toml = TomlValues.parse(body);
    final String name = TomlValues.required(toml, CHECK);
    try {
      final Optional<GenericKind> generic = GenericKind.select(name, toml);
      return generic.isPresent()
          ? GenericChecks.build(generic.get(), toml, ModifierTables.forLanguages(languages))
          : create(name, toml);
    } catch (final IllegalArgumentException exception) {
      throw new RuleConfigurationException(
          "check '" + name + "' cannot be used: " + exception.getMessage(), exception);
    } catch (final TomlInvalidTypeException exception) {
      throw new RuleConfigurationException(
          "check '" + name + "' has a parameter of the wrong type", exception);
    }
  }

  private RuleCheck create(final String name, final TomlParseResult toml) {
    return registry
        .find(name)
        .orElseThrow(() -> new RuleConfigurationException("unknown check '" + name + "'"))
        .create(toml);
  }
}
