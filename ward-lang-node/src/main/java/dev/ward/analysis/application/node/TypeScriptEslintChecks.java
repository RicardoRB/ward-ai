package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.node.lint.BanTsCommentCheck;
import dev.ward.analysis.domain.node.lint.DeclarationMergingCheck;
import dev.ward.analysis.domain.node.lint.DuplicateConstituentsCheck;
import dev.ward.analysis.domain.node.lint.DuplicateEnumValuesCheck;
import dev.ward.analysis.domain.node.lint.ExtraNonNullAssertionCheck;
import dev.ward.analysis.domain.node.lint.MisusedNewCheck;
import dev.ward.analysis.domain.node.lint.NoExplicitAnyCheck;
import dev.ward.analysis.domain.node.lint.NonNullOptionalChainCheck;
import dev.ward.analysis.domain.node.lint.PreferAsConstCheck;
import dev.ward.analysis.domain.node.lint.RedundantConstituentsCheck;
import dev.ward.analysis.domain.node.lint.ThisAliasCheck;
import dev.ward.analysis.domain.node.lint.TripleSlashReferenceCheck;
import dev.ward.analysis.domain.node.lint.UselessEmptyExportCheck;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tomlj.TomlParseResult;

/**
 * The TypeScript and JavaScript checks that only the typescript-eslint rules use (issue #327). The
 * syntax-only checks are here; the checks that read type facts are in {@link
 * TypeScriptEslintTypedChecks}.
 */
final class TypeScriptEslintChecks {

  private static final String MESSAGE = "message";
  private static final String[] DIRECTIVES = {
    "ts-check", "ts-expect-error", "ts-ignore", "ts-nocheck"
  };

  private TypeScriptEslintChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    final Map<String, CheckFactory> syntax = new LinkedHashMap<>();
    putSyntax(syntax);
    return CheckRegistry.union(List.of(syntax, TypeScriptEslintTypedChecks.checks()));
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
    checks.put("node-no-explicit-any", TypeScriptEslintChecks::explicitAny);
    checks.put("node-no-this-alias", TypeScriptEslintChecks::thisAlias);
    checks.put(
        "node-no-duplicate-type-constituents", TypeScriptEslintChecks::duplicateConstituents);
    checks.put("node-no-misused-new", TypeScriptEslintChecks::misusedNew);
    checks.put(
        "node-no-redundant-type-constituents",
        toml -> new RedundantConstituentsCheck(message(toml)));
    checks.put("node-ban-ts-comment", TypeScriptEslintChecks::banTsComment);
    checks.put("node-triple-slash-reference", TypeScriptEslintChecks::tripleSlash);
  }

  static String message(final TomlParseResult toml) {
    return TomlValues.required(toml, MESSAGE);
  }

  /** Reads a boolean option, or the tool's default when the rule body leaves it out. */
  static boolean flag(final TomlParseResult toml, final String key, final boolean fallback) {
    final Boolean value = toml.getBoolean(key);
    return value == null ? fallback : value;
  }

  private static NoExplicitAnyCheck explicitAny(final TomlParseResult toml) {
    return new NoExplicitAnyCheck(
        Boolean.TRUE.equals(toml.getBoolean("ignore-rest-args")), message(toml));
  }

  private static ThisAliasCheck thisAlias(final TomlParseResult toml) {
    return new ThisAliasCheck(
        !Boolean.FALSE.equals(toml.getBoolean("allow-destructuring")),
        TomlValues.optionalStrings(toml, "allowed-names"),
        message(toml));
  }

  private static DuplicateConstituentsCheck duplicateConstituents(final TomlParseResult toml) {
    return new DuplicateConstituentsCheck(
        Boolean.TRUE.equals(toml.getBoolean("ignore-unions")),
        Boolean.TRUE.equals(toml.getBoolean("ignore-intersections")),
        message(toml));
  }

  private static TripleSlashReferenceCheck tripleSlash(final TomlParseResult toml) {
    return new TripleSlashReferenceCheck(
        toml.getString("lib"), toml.getString("path"), toml.getString("types"), message(toml));
  }

  private static MisusedNewCheck misusedNew(final TomlParseResult toml) {
    return new MisusedNewCheck(
        TomlValues.required(toml, "class-message"), TomlValues.required(toml, "interface-message"));
  }

  private static BanTsCommentCheck banTsComment(final TomlParseResult toml) {
    final Map<String, String> directives = new LinkedHashMap<>();
    for (final String directive : DIRECTIVES) {
      final String value = toml.getString(directive);
      if (value != null) {
        directives.put(directive, value);
      }
    }
    final String format = toml.getString("description-format");
    return new BanTsCommentCheck(
        directives,
        TomlValues.integer(toml, "minimum-description-length"),
        format == null ? "" : format,
        message(toml));
  }
}
