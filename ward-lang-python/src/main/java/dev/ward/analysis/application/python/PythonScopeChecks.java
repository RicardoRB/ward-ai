package dev.ward.analysis.application.python;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AncestorCheck;
import dev.ward.analysis.domain.rule.check.DotFormatCheck;
import dev.ward.analysis.domain.rule.check.LoggingArgsCheck;
import dev.ward.analysis.domain.rule.check.ModuleOrderCheck;
import dev.ward.analysis.domain.rule.check.OutsideScopeCheck;
import dev.ward.analysis.domain.rule.check.PercentFormatCheck;
import dev.ward.analysis.domain.rule.check.RedefinitionCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.StarImportUsageCheck;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.tomlj.TomlParseResult;

/** The declarative checks that read the scopes or the format strings of a literal. */
final class PythonScopeChecks {

  private static final String MESSAGE = "message";
  private static final String KINDS = "kinds";

  private PythonScopeChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(build()));
  }

  /** Builds the check {@code name} from its table. */
  private static Map<String, CheckFactory> build() {
    return Map.ofEntries(
        entry("outside-scope", toml -> outsideScope(toml)),
        entry("redefinition", toml -> new RedefinitionCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "star-import-usage",
            toml -> new StarImportUsageCheck(TomlValues.required(toml, MESSAGE))),
        entry("percent-format", toml -> percentFormat(toml)),
        entry("dot-format", toml -> dotFormat(toml)),
        entry("ancestor-check", toml -> ancestorCheck(toml)),
        entry("module-order", toml -> moduleOrder(toml)),
        entry("logging-args", toml -> loggingArgs(toml)));
  }

  private static RuleCheck outsideScope(final TomlParseResult toml) {
    final Set<ScopeKind> within =
        TomlValues.strings(toml, "within").stream()
            .map(ScopeKind::valueOf)
            .collect(Collectors.toUnmodifiableSet());
    return new OutsideScopeCheck(
        TomlValues.strings(toml, KINDS), within, TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck ancestorCheck(final TomlParseResult toml) {
    final String mode = TomlValues.required(toml, "mode").toUpperCase(Locale.ROOT);
    final String text = toml.getString("text");
    return new AncestorCheck(
        TomlValues.strings(toml, KINDS),
        new AncestorCheck.Boundary(
            TomlValues.strings(toml, "allowed"), TomlValues.strings(toml, "stop")),
        AncestorCheck.Mode.valueOf(mode),
        Optional.ofNullable(text).map(Pattern::compile),
        TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck percentFormat(final TomlParseResult toml) {
    final String problem =
        TomlValues.required(toml, "problem").toUpperCase(Locale.ROOT).replace('-', '_');
    return new PercentFormatCheck(
        PercentFormatCheck.Problem.valueOf(problem), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck dotFormat(final TomlParseResult toml) {
    final String problem =
        TomlValues.required(toml, "problem").toUpperCase(Locale.ROOT).replace('-', '_');
    return new DotFormatCheck(
        DotFormatCheck.Problem.valueOf(problem), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck moduleOrder(final TomlParseResult toml) {
    final String problem =
        TomlValues.required(toml, "problem").toUpperCase(Locale.ROOT).replace('-', '_');
    return new ModuleOrderCheck(
        ModuleOrderCheck.Problem.valueOf(problem), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck loggingArgs(final TomlParseResult toml) {
    final String problem =
        TomlValues.required(toml, "problem").toUpperCase(Locale.ROOT).replace('-', '_');
    return new LoggingArgsCheck(
        LoggingArgsCheck.Problem.valueOf(problem), TomlValues.required(toml, MESSAGE));
  }
}
