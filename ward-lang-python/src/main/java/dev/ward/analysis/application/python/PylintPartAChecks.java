package dev.ward.analysis.application.python;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AndOrTernaryCheck;
import dev.ward.analysis.domain.rule.check.AttributeOutsideInitCheck;
import dev.ward.analysis.domain.rule.check.BadSuperCallCheck;
import dev.ward.analysis.domain.rule.check.BareCapturePatternCheck;
import dev.ward.analysis.domain.rule.check.ConcatJoinCheck;
import dev.ward.analysis.domain.rule.check.ConfusingWithCheck;
import dev.ward.analysis.domain.rule.check.ContextManagerCleanupCheck;
import dev.ward.analysis.domain.rule.check.DuplicateArgumentNameCheck;
import dev.ward.analysis.domain.rule.check.DuplicateFormatArgumentCheck;
import dev.ward.analysis.domain.rule.check.ExceptSubclassOrderCheck;
import dev.ward.analysis.domain.rule.check.InitGeneratorCheck;
import dev.ward.analysis.domain.rule.check.MinMaxBuiltinCheck;
import dev.ward.analysis.domain.rule.check.MixedComparisonChainCheck;
import dev.ward.analysis.domain.rule.check.OpenWithoutWithCheck;
import dev.ward.analysis.domain.rule.check.RedefinedFunctionCheck;
import dev.ward.analysis.domain.rule.check.SlotsCheck;
import dev.ward.analysis.domain.rule.check.StarUnpackLimitCheck;
import dev.ward.analysis.domain.rule.check.SwapVariablesCheck;
import dev.ward.analysis.domain.rule.check.SysExitCallCheck;
import dev.ward.analysis.domain.rule.check.ThreadInstantiationCheck;
import dev.ward.analysis.domain.rule.check.UnassignedCallExpressionCheck;
import java.util.List;
import java.util.Map;

/** The Pylint checks of part A that are declarative rules over the syntax tree or the scopes. */
final class PylintPartAChecks {

  private static final String MESSAGE = "message";

  private PylintPartAChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(checksFirst(), checksSecond()));
  }

  private static Map<String, CheckFactory> checksFirst() {
    return Map.ofEntries(
        entry(
            "except-subclass-order",
            toml -> new ExceptSubclassOrderCheck(TomlValues.required(toml, MESSAGE))),
        entry("init-generator", toml -> new InitGeneratorCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unassigned-call-expression",
            toml -> new UnassignedCallExpressionCheck(TomlValues.required(toml, MESSAGE))),
        entry("and-or-ternary", toml -> new AndOrTernaryCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "thread-instantiation",
            toml -> new ThreadInstantiationCheck(TomlValues.required(toml, MESSAGE))),
        entry("bad-super-call", toml -> new BadSuperCallCheck(TomlValues.required(toml, MESSAGE))),
        entry("confusing-with", toml -> new ConfusingWithCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "min-max-builtin",
            toml ->
                new MinMaxBuiltinCheck(
                    TomlValues.required(toml, "builtin"), TomlValues.required(toml, MESSAGE))),
        entry(
            "mixed-comparison-chain",
            toml -> new MixedComparisonChainCheck(TomlValues.required(toml, MESSAGE))),
        entry("swap-variables", toml -> new SwapVariablesCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "duplicate-format-argument",
            toml -> new DuplicateFormatArgumentCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "open-without-with",
            toml -> new OpenWithoutWithCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static Map<String, CheckFactory> checksSecond() {
    return Map.ofEntries(
        entry(
            "contextmanager-cleanup",
            toml -> new ContextManagerCleanupCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "bare-capture-pattern",
            toml -> new BareCapturePatternCheck(TomlValues.required(toml, MESSAGE))),
        entry("concat-join", toml -> new ConcatJoinCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "slots-conflict",
            toml ->
                new SlotsCheck(
                    SlotsCheck.Problem.CLASS_VARIABLE_CONFLICT,
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "slots-declare",
            toml ->
                new SlotsCheck(
                    SlotsCheck.Problem.DECLARE_NON_SLOT, TomlValues.required(toml, MESSAGE))),
        entry(
            "slots-assign",
            toml ->
                new SlotsCheck(
                    SlotsCheck.Problem.ASSIGNING_NON_SLOT, TomlValues.required(toml, MESSAGE))),
        entry(
            "attribute-outside-init",
            toml -> new AttributeOutsideInitCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "star-unpack-limit",
            toml -> new StarUnpackLimitCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "duplicate-argument-name",
            toml -> new DuplicateArgumentNameCheck(TomlValues.required(toml, MESSAGE))),
        entry("sys-exit-call", toml -> new SysExitCallCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "function-redefined",
            toml -> new RedefinedFunctionCheck(TomlValues.required(toml, MESSAGE))));
  }
}
