package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.AvoidModuleImportCheck;
import dev.ward.analysis.domain.rule.check.CustomImportOrderCheck;
import dev.ward.analysis.domain.rule.check.IllegalImportCheck;
import dev.ward.analysis.domain.rule.check.ImportControlCheck;
import dev.ward.analysis.domain.rule.check.ImportOrderCheck;
import dev.ward.analysis.domain.rule.check.ModuleDirectiveOrderCheck;
import dev.ward.analysis.domain.rule.check.ModuleImportOrderCheck;
import dev.ward.analysis.domain.rule.check.RedundantImportCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.StaticImportCheck;
import dev.ward.analysis.domain.rule.check.UnnecessaryFullyQualifiedTypeCheck;
import java.util.Optional;
import java.util.Set;
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

  private static final Set<String> CHECKS =
      Set.of(
          "static-import",
          "illegal-import",
          "redundant-import",
          "unnecessary-qualified-type",
          "import-order",
          "custom-import-order",
          "import-control",
          "module-directive-order",
          "module-import-order",
          "avoid-module-import");

  private ImportChecks() {}

  /** Returns whether {@code name} is one of the import and module checks. */
  static boolean isImportCheck(final String name) {
    return CHECKS.contains(name);
  }

  /**
   * Builds the check named {@code name}.
   *
   * @throws RuleConfigurationException if the name is unknown or a parameter is invalid
   */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "static-import" ->
          new StaticImportCheck(TomlValues.optionalStrings(toml, EXCLUDES), message(toml));
      case "illegal-import" ->
          new IllegalImportCheck(
              TomlLists.optional(toml, "illegal-pkgs"),
              TomlLists.optional(toml, "illegal-classes"),
              flag(toml, "regexp", false),
              message(toml));
      case "redundant-import" -> new RedundantImportCheck(message(toml));
      case "unnecessary-qualified-type" -> new UnnecessaryFullyQualifiedTypeCheck(message(toml));
      case "import-order" ->
          new ImportOrderCheck(
              TomlLists.optional(toml, "groups"),
              flag(toml, "ordered", true),
              flag(toml, "separated", false),
              flag(toml, "case-sensitive", true),
              message(toml));
      case "custom-import-order" -> customImportOrder(toml);
      case "import-control" ->
          new ImportControlCheck(
              TomlLists.optional(toml, "rules"),
              Pattern.compile(TomlValues.required(toml, "path")),
              message(toml));
      default -> moduleCheck(name, toml);
    };
  }

  /** Builds the checks of module declarations and module imports. */
  private static RuleCheck moduleCheck(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "module-directive-order" ->
          new ModuleDirectiveOrderCheck(
              TomlLists.optional(toml, "order"),
              flag(toml, "validate-block-separation", true),
              message(toml));
      case "module-import-order" ->
          new ModuleImportOrderCheck(
              topOrBottom(toml), flag(toml, "separated", false), message(toml));
      case "avoid-module-import" ->
          new AvoidModuleImportCheck(
              TomlValues.optionalStrings(toml, EXCLUDES),
              TomlValues.optionalInteger(toml, "max-allowed-module-imports", 0),
              message(toml));
      default -> throw new RuleConfigurationException("unknown check '" + name + "'");
    };
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
