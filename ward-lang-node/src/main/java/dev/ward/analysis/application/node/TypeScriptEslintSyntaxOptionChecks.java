package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.node.lint.BanTsCommentCheck;
import dev.ward.analysis.domain.node.lint.DuplicateConstituentsCheck;
import dev.ward.analysis.domain.node.lint.MisusedNewCheck;
import dev.ward.analysis.domain.node.lint.NoExplicitAnyCheck;
import dev.ward.analysis.domain.node.lint.NoNamespaceCheck;
import dev.ward.analysis.domain.node.lint.ThisAliasCheck;
import dev.ward.analysis.domain.node.lint.TripleSlashReferenceCheck;
import java.util.LinkedHashMap;
import java.util.Map;
import org.tomlj.TomlParseResult;

/**
 * The typescript-eslint syntax checks whose rule options change what they report (issue #327). The
 * other syntax-only checks are in {@link TypeScriptEslintChecks}.
 */
final class TypeScriptEslintSyntaxOptionChecks {

  private static final String[] DIRECTIVES = {
    "ts-check", "ts-expect-error", "ts-ignore", "ts-nocheck"
  };

  private TypeScriptEslintSyntaxOptionChecks() {}

  /** The option-driven syntax checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    final Map<String, CheckFactory> checks = new LinkedHashMap<>();
    checks.put("node-no-explicit-any", TypeScriptEslintSyntaxOptionChecks::explicitAny);
    checks.put("node-no-this-alias", TypeScriptEslintSyntaxOptionChecks::thisAlias);
    checks.put("node-no-namespace", TypeScriptEslintSyntaxOptionChecks::namespace);
    checks.put(
        "node-no-duplicate-type-constituents",
        TypeScriptEslintSyntaxOptionChecks::duplicateConstituents);
    checks.put("node-no-misused-new", TypeScriptEslintSyntaxOptionChecks::misusedNew);
    checks.put("node-ban-ts-comment", TypeScriptEslintSyntaxOptionChecks::banTsComment);
    checks.put("node-triple-slash-reference", TypeScriptEslintSyntaxOptionChecks::tripleSlash);
    return checks;
  }

  private static NoExplicitAnyCheck explicitAny(final TomlParseResult toml) {
    return new NoExplicitAnyCheck(
        Boolean.TRUE.equals(toml.getBoolean("ignore-rest-args")),
        TypeScriptEslintChecks.message(toml));
  }

  private static NoNamespaceCheck namespace(final TomlParseResult toml) {
    return new NoNamespaceCheck(
        TypeScriptEslintChecks.flag(toml, "allow-declarations", false),
        TypeScriptEslintChecks.flag(toml, "allow-definition-files", true),
        TypeScriptEslintChecks.message(toml));
  }

  private static ThisAliasCheck thisAlias(final TomlParseResult toml) {
    return new ThisAliasCheck(
        !Boolean.FALSE.equals(toml.getBoolean("allow-destructuring")),
        TomlValues.optionalStrings(toml, "allowed-names"),
        TypeScriptEslintChecks.message(toml));
  }

  private static DuplicateConstituentsCheck duplicateConstituents(final TomlParseResult toml) {
    return new DuplicateConstituentsCheck(
        Boolean.TRUE.equals(toml.getBoolean("ignore-unions")),
        Boolean.TRUE.equals(toml.getBoolean("ignore-intersections")),
        TypeScriptEslintChecks.message(toml));
  }

  private static TripleSlashReferenceCheck tripleSlash(final TomlParseResult toml) {
    return new TripleSlashReferenceCheck(
        toml.getString("lib"),
        toml.getString("path"),
        toml.getString("types"),
        TypeScriptEslintChecks.message(toml));
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
        TypeScriptEslintChecks.message(toml));
  }
}
