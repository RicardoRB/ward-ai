package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlLists;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AvoidModuleImportCheck;
import dev.ward.analysis.domain.rule.check.CustomImportOrderCheck;
import dev.ward.analysis.domain.rule.check.IllegalImportCheck;
import dev.ward.analysis.domain.rule.check.ImportControlCheck;
import dev.ward.analysis.domain.rule.check.ImportOrderCheck;
import dev.ward.analysis.domain.rule.check.ModuleDirectiveOrderCheck;
import dev.ward.analysis.domain.rule.check.ModuleImportOrderCheck;
import dev.ward.analysis.domain.rule.check.RedundantImportCheck;
import dev.ward.analysis.domain.rule.check.StaticImportCheck;
import dev.ward.analysis.domain.rule.check.UnnecessaryFullyQualifiedTypeCheck;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/**
 * Builds the Java import and module checks that a rule body names, with their parameters. Each
 * parameter is a rule option, named after the Checkstyle property in kebab-case.
 */
final class ImportChecks {

  private static final String MESSAGE = "message";
  private static final String EXCLUDES = "excludes";
  private static final String TOP = "top";
  private static final String BOTTOM = "bottom";

  private ImportChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(build(), moduleCheck()));
  }

  private static Map<String, CheckFactory> build() {
    return Map.ofEntries(
        entry(
            "static-import",
            toml ->
                new StaticImportCheck(TomlValues.optionalStrings(toml, EXCLUDES), message(toml))),
        entry(
            "illegal-import",
            toml ->
                new IllegalImportCheck(
                    TomlLists.optional(toml, "illegal-pkgs"),
                    TomlLists.optional(toml, "illegal-classes"),
                    flag(toml, "regexp", false),
                    message(toml))),
        entry("redundant-import", toml -> new RedundantImportCheck(message(toml))),
        entry(
            "unnecessary-qualified-type",
            toml -> new UnnecessaryFullyQualifiedTypeCheck(message(toml))),
        entry(
            "import-order",
            toml ->
                new ImportOrderCheck(
                    TomlLists.optional(toml, "groups"),
                    flag(toml, "ordered", true),
                    flag(toml, "separated", false),
                    flag(toml, "case-sensitive", true),
                    message(toml))),
        entry("custom-import-order", ImportChecks::customImportOrder),
        entry(
            "import-control",
            toml ->
                new ImportControlCheck(
                    TomlLists.optional(toml, "rules"),
                    Pattern.compile(TomlValues.required(toml, "path")),
                    message(toml))));
  }

  /** Builds the checks of module declarations and module imports. */
  private static Map<String, CheckFactory> moduleCheck() {
    return Map.ofEntries(
        entry(
            "module-directive-order",
            toml ->
                new ModuleDirectiveOrderCheck(
                    TomlLists.optional(toml, "order"),
                    flag(toml, "validate-block-separation", true),
                    message(toml))),
        entry(
            "module-import-order",
            toml ->
                new ModuleImportOrderCheck(
                    topOrBottom(toml), flag(toml, "separated", false), message(toml))),
        entry(
            "avoid-module-import",
            toml ->
                new AvoidModuleImportCheck(
                    TomlValues.optionalStrings(toml, EXCLUDES),
                    TomlValues.optionalInteger(toml, "max-allowed-module-imports", 0),
                    message(toml))));
  }

  private static CustomImportOrderCheck customImportOrder(final TomlParseResult toml) {
    return new CustomImportOrderCheck(
        new CustomImportOrderCheck.Rules(
            TomlValues.required(toml, "custom-import-order-rules"),
            TomlValues.required(toml, "special-imports-regexp"),
            TomlValues.required(toml, "standard-package-regexp"),
            TomlValues.required(toml, "third-party-package-regexp")),
        flag(toml, "sort-imports-in-group-alphabetically", false),
        flag(toml, "separate-line-between-groups", true),
        message(toml));
  }

  /** The module import position: {@code top} or {@code bottom}. */
  private static boolean topOrBottom(final TomlParseResult toml) {
    final String option = TomlValues.required(toml, "option");
    if (TOP.equals(option)) {
      return true;
    }
    if (BOTTOM.equals(option)) {
      return false;
    }
    throw new IllegalArgumentException("option must be top or bottom, not '" + option + "'");
  }

  private static boolean flag(
      final TomlParseResult toml, final String key, final boolean fallback) {
    return Optional.ofNullable(toml.getBoolean(key)).orElse(fallback);
  }

  private static String message(final TomlParseResult toml) {
    return TomlValues.required(toml, MESSAGE);
  }
}
