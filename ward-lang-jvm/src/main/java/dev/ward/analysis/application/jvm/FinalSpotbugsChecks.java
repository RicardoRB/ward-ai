package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ClassShapeCheck;
import dev.ward.analysis.domain.rule.check.InferredIgnoredResultCheck;
import dev.ward.analysis.domain.rule.check.KnownNullCheck;
import dev.ward.analysis.domain.rule.check.NullReturnDereferenceCheck;
import dev.ward.analysis.domain.rule.check.NullableParameterCheck;
import dev.ward.analysis.domain.rule.check.OverrideNullnessCheck;
import dev.ward.analysis.domain.rule.check.PartialConstructionCheck;
import dev.ward.analysis.domain.rule.check.SynchronizedNullCheckCheck;
import dev.ward.analysis.domain.rule.check.UnlockWithoutLockCheck;
import dev.ward.analysis.domain.rule.check.UnwrittenPublicFieldCheck;
import java.util.Locale;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** Builds the checks of the last SpotBugs patterns: class shape, resources and null handling. */
final class FinalSpotbugsChecks {

  private static final String MESSAGE = "message";

  private FinalSpotbugsChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry("class-shape", FinalSpotbugsChecks::classShape),
        entry("known-null", FinalSpotbugsChecks::knownNull),
        entry(
            "unwritten-public-field",
            toml -> new UnwrittenPublicFieldCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "partial-construction",
            toml -> new PartialConstructionCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unlock-without-lock",
            toml ->
                new UnlockWithoutLockCheck(
                    TomlValues.strings(toml, "types"), TomlValues.required(toml, MESSAGE))),
        entry(
            "null-return-dereference",
            toml -> new NullReturnDereferenceCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "inferred-ignored-result",
            toml -> new InferredIgnoredResultCheck(TomlValues.required(toml, MESSAGE))),
        entry("override-nullness", FinalSpotbugsChecks::overrideNullness),
        entry(
            "synchronized-null-check",
            toml -> new SynchronizedNullCheckCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "nullable-parameter-dereferenced",
            toml -> new NullableParameterCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static OverrideNullnessCheck overrideNullness(final TomlParseResult toml) {
    return new OverrideNullnessCheck(
        OverrideNullnessCheck.Mode.valueOf(
            TomlValues.required(toml, "mode").replace('-', '_').toUpperCase(Locale.ROOT)),
        TomlValues.strings(toml, "nullable"),
        TomlValues.strings(toml, "nonnull"),
        TomlValues.required(toml, MESSAGE));
  }

  private static KnownNullCheck knownNull(final TomlParseResult toml) {
    return new KnownNullCheck(
        KnownNullCheck.Mode.valueOf(
            TomlValues.required(toml, "mode").replace('-', '_').toUpperCase(Locale.ROOT)),
        TomlValues.required(toml, MESSAGE));
  }

  private static ClassShapeCheck classShape(final TomlParseResult toml) {
    return new ClassShapeCheck(
        ClassShapeCheck.Mode.valueOf(
            TomlValues.required(toml, "mode").replace('-', '_').toUpperCase(Locale.ROOT)),
        TomlValues.required(toml, MESSAGE));
  }
}
