package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.check.DotFormatCheck;
import dev.ward.analysis.domain.rule.check.LoggingArgsCheck;
import dev.ward.analysis.domain.rule.check.OutsideScopeCheck;
import dev.ward.analysis.domain.rule.check.PercentFormatCheck;
import dev.ward.analysis.domain.rule.check.RedefinitionCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.StarImportUsageCheck;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.Set;
import java.util.stream.Collectors;
import org.tomlj.TomlParseResult;

/** The declarative checks that read the scopes or the format strings of a literal. */
final class ScopeAndFormatChecks {

  private static final String MESSAGE = "message";
  private static final String KINDS = "kinds";

  private ScopeAndFormatChecks() {}

  /** Whether {@code name} is a check of this family. */
  static boolean isCheck(final String name) {
    return switch (name) {
      case "outside-scope",
          "redefinition",
          "star-import-usage",
          "percent-format",
          "dot-format",
          "logging-args" ->
          true;
      default -> false;
    };
  }

  /** Builds the check {@code name} from its table. */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "outside-scope" -> outsideScope(toml);
      case "redefinition" -> new RedefinitionCheck(TomlValues.required(toml, MESSAGE));
      case "star-import-usage" -> new StarImportUsageCheck(TomlValues.required(toml, MESSAGE));
      case "percent-format" -> percentFormat(toml);
      case "dot-format" -> dotFormat(toml);
      default -> loggingArgs(toml);
    };
  }

  private static RuleCheck outsideScope(final TomlParseResult toml) {
    final Set<ScopeKind> within =
        TomlValues.strings(toml, "within").stream()
            .map(ScopeKind::valueOf)
            .collect(Collectors.toUnmodifiableSet());
    return new OutsideScopeCheck(
        TomlValues.strings(toml, KINDS), within, TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck percentFormat(final TomlParseResult toml) {
    final String problem =
        TomlValues.required(toml, "problem").toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    return new PercentFormatCheck(
        PercentFormatCheck.Problem.valueOf(problem), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck dotFormat(final TomlParseResult toml) {
    final String problem =
        TomlValues.required(toml, "problem").toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    return new DotFormatCheck(
        DotFormatCheck.Problem.valueOf(problem), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck loggingArgs(final TomlParseResult toml) {
    final String problem =
        TomlValues.required(toml, "problem").toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    return new LoggingArgsCheck(
        LoggingArgsCheck.Problem.valueOf(problem), TomlValues.required(toml, MESSAGE));
  }
}
