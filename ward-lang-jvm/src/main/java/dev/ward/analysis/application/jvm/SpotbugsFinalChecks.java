package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AssertOnParameterCheck;
import dev.ward.analysis.domain.rule.check.CastTypesCheck;
import dev.ward.analysis.domain.rule.check.ConstructorThrowCheck;
import dev.ward.analysis.domain.rule.check.CovariantCompareToCheck;
import dev.ward.analysis.domain.rule.check.DeadStoreCheck;
import dev.ward.analysis.domain.rule.check.EntrySetReuseCheck;
import dev.ward.analysis.domain.rule.check.FloatingCompareToCheck;
import dev.ward.analysis.domain.rule.check.NonSerializableWriteCheck;
import dev.ward.analysis.domain.rule.check.TernaryCoercionCheck;
import dev.ward.analysis.domain.rule.check.UncheckedEqualsCastCheck;
import dev.ward.analysis.domain.rule.check.UnconfirmedCastCheck;
import dev.ward.analysis.domain.rule.check.UnsharedBoxedLockCheck;
import java.util.Locale;
import java.util.Map;

/**
 * The checks of the Java rules ported from SpotBugs that read class shape, casts and dead stores.
 */
final class SpotbugsFinalChecks {

  private static final String MESSAGE = "message";

  private SpotbugsFinalChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "covariant-compare-to",
            toml ->
                new CovariantCompareToCheck(
                    toml.getBoolean("abstract-class", () -> false),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "constructor-throw",
            toml -> new ConstructorThrowCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "assert-on-parameter",
            toml -> new AssertOnParameterCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "cast-types",
            toml ->
                new CastTypesCheck(
                    TomlValues.strings(toml, "from"),
                    TomlValues.strings(toml, "to"),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "ternary-coercion",
            toml -> new TernaryCoercionCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "floating-compare-to",
            toml -> new FloatingCompareToCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unchecked-equals-cast",
            toml -> new UncheckedEqualsCastCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unconfirmed-cast",
            toml ->
                new UnconfirmedCastCheck(
                    toml.getBoolean("call-result", () -> false),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "entry-set-reuse", toml -> new EntrySetReuseCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "non-serializable-write",
            toml -> new NonSerializableWriteCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unshared-boxed-lock",
            toml -> new UnsharedBoxedLockCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "dead-store",
            toml ->
                new DeadStoreCheck(
                    DeadStoreCheck.Kind.valueOf(
                        TomlValues.required(toml, "kind").toUpperCase(Locale.ROOT)),
                    TomlValues.required(toml, MESSAGE))));
  }
}
