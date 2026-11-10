package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.node.lint.DeclarationMergingCheck;
import dev.ward.analysis.domain.node.lint.DuplicateEnumValuesCheck;
import dev.ward.analysis.domain.node.lint.ExtraNonNullAssertionCheck;
import dev.ward.analysis.domain.node.lint.NonNullOptionalChainCheck;
import dev.ward.analysis.domain.node.lint.PreferAsConstCheck;
import dev.ward.analysis.domain.node.lint.RedundantConstituentsCheck;
import dev.ward.analysis.domain.node.lint.UselessEmptyExportCheck;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tomlj.TomlParseResult;

/**
 * The TypeScript and JavaScript checks that only the typescript-eslint rules use (issue #327). The
 * syntax checks that take no rule option are here; the option-driven ones are in {@link
 * TypeScriptEslintSyntaxOptionChecks}, and the checks that read type facts in {@link
 * TypeScriptEslintTypedChecks}.
 */
final class TypeScriptEslintChecks {

  private static final String MESSAGE = "message";

  private TypeScriptEslintChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    final Map<String, CheckFactory> syntax = new LinkedHashMap<>();
    putSyntax(syntax);
    return CheckRegistry.union(
        List.of(
            syntax,
            TypeScriptEslintSyntaxOptionChecks.checks(),
            TypeScriptEslintTypedChecks.checks()));
  }

  private static void putSyntax(final Map<String, CheckFactory> checks) {
    checks.put(
        "node-no-extra-non-null-assertion", toml -> new ExtraNonNullAssertionCheck(message(toml)));
    checks.put(
        "node-no-non-null-asserted-optional-chain",
        toml -> new NonNullOptionalChainCheck(message(toml)));
    checks.put("node-no-useless-empty-export", toml -> new UselessEmptyExportCheck(message(toml)));
    checks.put("node-prefer-as-const", toml -> new PreferAsConstCheck(message(toml)));
    checks.put(
        "node-no-duplicate-enum-values", toml -> new DuplicateEnumValuesCheck(message(toml)));
    checks.put(
        "node-no-unsafe-declaration-merging", toml -> new DeclarationMergingCheck(message(toml)));
    checks.put(
        "node-no-redundant-type-constituents",
        toml -> new RedundantConstituentsCheck(message(toml)));
  }

  static String message(final TomlParseResult toml) {
    return TomlValues.required(toml, MESSAGE);
  }

  /** Reads a boolean option, or the tool's default when the rule body leaves it out. */
  static boolean flag(final TomlParseResult toml, final String key, final boolean fallback) {
    final Boolean value = toml.getBoolean(key);
    return value == null ? fallback : value;
  }
}
