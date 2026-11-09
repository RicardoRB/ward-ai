package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.node.lint.AwaitThenableCheck;
import dev.ward.analysis.domain.node.lint.BanTsCommentCheck;
import dev.ward.analysis.domain.node.lint.DeclarationMergingCheck;
import dev.ward.analysis.domain.node.lint.DuplicateConstituentsCheck;
import dev.ward.analysis.domain.node.lint.DuplicateEnumValuesCheck;
import dev.ward.analysis.domain.node.lint.ExtraNonNullAssertionCheck;
import dev.ward.analysis.domain.node.lint.FloatingPromiseCheck;
import dev.ward.analysis.domain.node.lint.MisusedNewCheck;
import dev.ward.analysis.domain.node.lint.MisusedPromiseCheck;
import dev.ward.analysis.domain.node.lint.NoArrayDeleteCheck;
import dev.ward.analysis.domain.node.lint.NoForInArrayCheck;
import dev.ward.analysis.domain.node.lint.NonNullOptionalChainCheck;
import dev.ward.analysis.domain.node.lint.OnlyThrowErrorCheck;
import dev.ward.analysis.domain.node.lint.PreferAsConstCheck;
import dev.ward.analysis.domain.node.lint.RestrictOperandsCheck;
import dev.ward.analysis.domain.node.lint.RestrictTemplateCheck;
import dev.ward.analysis.domain.node.lint.ThisAliasCheck;
import dev.ward.analysis.domain.node.lint.UnnecessaryAssertionCheck;
import dev.ward.analysis.domain.node.lint.UnsafeAnyCheck;
import dev.ward.analysis.domain.node.lint.UselessEmptyExportCheck;
import java.util.LinkedHashMap;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** The declarative checks of the TypeScript rules, by their {@code check} name. */
public final class NodeCheckProvider implements CheckProvider {

  private static final String MESSAGE = "message";
  private static final String[] DIRECTIVES = {
    "ts-check", "ts-expect-error", "ts-ignore", "ts-nocheck"
  };

  @Override
  public Map<String, CheckFactory> checks() {
    final Map<String, CheckFactory> checks = new LinkedHashMap<>();
    putSyntax(checks);
    putTyped(checks);
    return checks;
  }

  private static void putSyntax(final Map<String, CheckFactory> checks) {
    checks.put(
        "ts-no-extra-non-null-assertion", toml -> new ExtraNonNullAssertionCheck(message(toml)));
    checks.put(
        "ts-no-non-null-asserted-optional-chain",
        toml -> new NonNullOptionalChainCheck(message(toml)));
    checks.put("ts-no-useless-empty-export", toml -> new UselessEmptyExportCheck(message(toml)));
    checks.put("ts-prefer-as-const", toml -> new PreferAsConstCheck(message(toml)));
    checks.put("ts-no-duplicate-enum-values", toml -> new DuplicateEnumValuesCheck(message(toml)));
    checks.put(
        "ts-no-unsafe-declaration-merging", toml -> new DeclarationMergingCheck(message(toml)));
    checks.put("ts-no-this-alias", NodeCheckProvider::thisAlias);
    checks.put("ts-no-duplicate-type-constituents", NodeCheckProvider::duplicateConstituents);
    checks.put("ts-no-misused-new", NodeCheckProvider::misusedNew);
    checks.put("ts-ban-ts-comment", NodeCheckProvider::banTsComment);
  }

  private static void putTyped(final Map<String, CheckFactory> checks) {
    checks.put("ts-await-thenable", toml -> new AwaitThenableCheck(message(toml)));
    checks.put("ts-no-for-in-array", toml -> new NoForInArrayCheck(message(toml)));
    checks.put("ts-no-array-delete", toml -> new NoArrayDeleteCheck(message(toml)));
    checks.put("ts-no-floating-promises", NodeCheckProvider::floatingPromises);
    checks.put("ts-no-misused-promises", toml -> new MisusedPromiseCheck(message(toml)));
    checks.put("ts-only-throw-error", toml -> new OnlyThrowErrorCheck(message(toml)));
    checks.put("ts-no-unsafe-assignment", toml -> unsafe(UnsafeAnyCheck.Site.ASSIGNMENT, toml));
    checks.put("ts-no-unsafe-call", toml -> unsafe(UnsafeAnyCheck.Site.CALL, toml));
    checks.put("ts-no-unsafe-member-access", toml -> unsafe(UnsafeAnyCheck.Site.MEMBER, toml));
    checks.put("ts-no-unsafe-return", toml -> unsafe(UnsafeAnyCheck.Site.RETURN, toml));
    checks.put("ts-no-unsafe-argument", toml -> unsafe(UnsafeAnyCheck.Site.ARGUMENT, toml));
    checks.put("ts-no-unsafe-unary-minus", toml -> unsafe(UnsafeAnyCheck.Site.NEGATION, toml));
    checks.put("ts-restrict-plus-operands", toml -> new RestrictOperandsCheck(message(toml)));
    checks.put(
        "ts-restrict-template-expressions", toml -> new RestrictTemplateCheck(message(toml)));
    checks.put(
        "ts-no-unnecessary-type-assertion", toml -> new UnnecessaryAssertionCheck(message(toml)));
  }

  private static String message(final TomlParseResult toml) {
    return TomlValues.required(toml, MESSAGE);
  }

  private static UnsafeAnyCheck unsafe(final UnsafeAnyCheck.Site site, final TomlParseResult toml) {
    return new UnsafeAnyCheck(site, message(toml));
  }

  private static FloatingPromiseCheck floatingPromises(final TomlParseResult toml) {
    return new FloatingPromiseCheck(
        !Boolean.FALSE.equals(toml.getBoolean("ignore-void")),
        Boolean.TRUE.equals(toml.getBoolean("ignore-iife")),
        message(toml));
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
    return new BanTsCommentCheck(
        directives, TomlValues.integer(toml, "minimum-description-length"), message(toml));
  }
}
