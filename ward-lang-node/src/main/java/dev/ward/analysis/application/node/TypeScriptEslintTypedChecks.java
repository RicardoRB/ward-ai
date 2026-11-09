package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.node.lint.AwaitThenableCheck;
import dev.ward.analysis.domain.node.lint.BaseToStringCheck;
import dev.ward.analysis.domain.node.lint.EnumComparisonCheck;
import dev.ward.analysis.domain.node.lint.FloatingPromiseCheck;
import dev.ward.analysis.domain.node.lint.MisusedPromiseCheck;
import dev.ward.analysis.domain.node.lint.NoArrayDeleteCheck;
import dev.ward.analysis.domain.node.lint.NoForInArrayCheck;
import dev.ward.analysis.domain.node.lint.OnlyThrowErrorCheck;
import dev.ward.analysis.domain.node.lint.OperandPolicy;
import dev.ward.analysis.domain.node.lint.RestrictOperandsCheck;
import dev.ward.analysis.domain.node.lint.RestrictTemplateCheck;
import dev.ward.analysis.domain.node.lint.TemplatePolicy;
import dev.ward.analysis.domain.node.lint.UnaryMinusCheck;
import dev.ward.analysis.domain.node.lint.UnboundMethodCheck;
import dev.ward.analysis.domain.node.lint.UnnecessaryAssertionCheck;
import dev.ward.analysis.domain.node.lint.UnsafeAnyCheck;
import java.util.LinkedHashMap;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** The typescript-eslint checks that read the native type facts (issue #327). */
final class TypeScriptEslintTypedChecks {

  private TypeScriptEslintTypedChecks() {}

  /** The typed checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    final Map<String, CheckFactory> checks = new LinkedHashMap<>();
    checks.put("node-await-thenable", TypeScriptEslintTypedChecks::awaitThenable);
    checks.put(
        "node-unbound-method",
        toml -> new UnboundMethodCheck(TypeScriptEslintChecks.message(toml)));
    checks.put(
        "node-no-base-to-string",
        toml -> new BaseToStringCheck(TypeScriptEslintChecks.message(toml)));
    checks.put(
        "node-no-unsafe-enum-comparison",
        toml -> new EnumComparisonCheck(TypeScriptEslintChecks.message(toml)));
    checks.put(
        "node-no-for-in-array",
        toml -> new NoForInArrayCheck(TypeScriptEslintChecks.message(toml)));
    checks.put(
        "node-no-array-delete",
        toml -> new NoArrayDeleteCheck(TypeScriptEslintChecks.message(toml)));
    checks.put("node-no-floating-promises", TypeScriptEslintTypedChecks::floatingPromises);
    checks.put("node-no-misused-promises", TypeScriptEslintTypedChecks::misusedPromises);
    checks.put("node-only-throw-error", TypeScriptEslintTypedChecks::onlyThrowError);
    checks.put("node-no-unsafe-assignment", toml -> unsafe(UnsafeAnyCheck.Site.ASSIGNMENT, toml));
    checks.put("node-no-unsafe-call", toml -> unsafe(UnsafeAnyCheck.Site.CALL, toml));
    checks.put("node-no-unsafe-member-access", toml -> unsafe(UnsafeAnyCheck.Site.MEMBER, toml));
    checks.put("node-no-unsafe-return", toml -> unsafe(UnsafeAnyCheck.Site.RETURN, toml));
    checks.put("node-no-unsafe-argument", toml -> unsafe(UnsafeAnyCheck.Site.ARGUMENT, toml));
    checks.put(
        "node-no-unsafe-unary-minus",
        toml -> new UnaryMinusCheck(TypeScriptEslintChecks.message(toml)));
    checks.put("node-restrict-plus-operands", TypeScriptEslintTypedChecks::restrictOperands);
    checks.put("node-restrict-template-expressions", TypeScriptEslintTypedChecks::restrictTemplate);
    checks.put(
        "node-no-unnecessary-type-assertion",
        toml -> new UnnecessaryAssertionCheck(TypeScriptEslintChecks.message(toml)));
    return checks;
  }

  private static UnsafeAnyCheck unsafe(final UnsafeAnyCheck.Site site, final TomlParseResult toml) {
    return new UnsafeAnyCheck(site, TypeScriptEslintChecks.message(toml));
  }

  private static AwaitThenableCheck awaitThenable(final TomlParseResult toml) {
    return new AwaitThenableCheck(
        TypeScriptEslintChecks.flag(toml, "allow-mixed-promise-arrays", false),
        TypeScriptEslintChecks.message(toml));
  }

  private static MisusedPromiseCheck misusedPromises(final TomlParseResult toml) {
    return new MisusedPromiseCheck(
        TypeScriptEslintChecks.flag(toml, "checks-conditionals", true),
        TypeScriptEslintChecks.flag(toml, "checks-spreads", true),
        TypeScriptEslintChecks.flag(toml, "checks-void-return", true),
        TypeScriptEslintChecks.message(toml));
  }

  private static OnlyThrowErrorCheck onlyThrowError(final TomlParseResult toml) {
    return new OnlyThrowErrorCheck(
        TypeScriptEslintChecks.flag(toml, "allow-rethrowing", true),
        TypeScriptEslintChecks.flag(toml, "allow-throwing-any", true),
        TypeScriptEslintChecks.flag(toml, "allow-throwing-unknown", true),
        TomlValues.optionalStrings(toml, "allow"),
        TypeScriptEslintChecks.message(toml));
  }

  private static RestrictOperandsCheck restrictOperands(final TomlParseResult toml) {
    final OperandPolicy policy =
        new OperandPolicy(
            TypeScriptEslintChecks.flag(toml, "allow-any", true),
            TypeScriptEslintChecks.flag(toml, "allow-boolean", true),
            TypeScriptEslintChecks.flag(toml, "allow-nullish", true),
            TypeScriptEslintChecks.flag(toml, "allow-number-and-string", true),
            TypeScriptEslintChecks.flag(toml, "allow-regexp", true),
            TypeScriptEslintChecks.flag(toml, "skip-compound-assignments", false));
    return new RestrictOperandsCheck(policy, TypeScriptEslintChecks.message(toml));
  }

  private static RestrictTemplateCheck restrictTemplate(final TomlParseResult toml) {
    final TemplatePolicy policy =
        new TemplatePolicy(
            TypeScriptEslintChecks.flag(toml, "allow-any", true),
            TypeScriptEslintChecks.flag(toml, "allow-array", false),
            TypeScriptEslintChecks.flag(toml, "allow-boolean", true),
            TypeScriptEslintChecks.flag(toml, "allow-nullish", true),
            TypeScriptEslintChecks.flag(toml, "allow-number", true),
            TypeScriptEslintChecks.flag(toml, "allow-regexp", true),
            TypeScriptEslintChecks.flag(toml, "allow-never", false),
            TomlValues.optionalStrings(toml, "allow"));
    return new RestrictTemplateCheck(policy, TypeScriptEslintChecks.message(toml));
  }

  private static FloatingPromiseCheck floatingPromises(final TomlParseResult toml) {
    return new FloatingPromiseCheck(
        !Boolean.FALSE.equals(toml.getBoolean("ignore-void")),
        Boolean.TRUE.equals(toml.getBoolean("ignore-iife")),
        TypeScriptEslintChecks.message(toml));
  }
}
