package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.DeduplicateConstantsCheck;
import dev.ward.analysis.domain.rule.check.DoNotMockCheck;
import dev.ward.analysis.domain.rule.check.EqualsWrongThingCheck;
import dev.ward.analysis.domain.rule.check.ExtendsAutoValueCheck;
import dev.ward.analysis.domain.rule.check.ForOverrideCheck;
import dev.ward.analysis.domain.rule.check.FormatStringAnnotationCheck;
import dev.ward.analysis.domain.rule.check.FormatStringCheck;
import dev.ward.analysis.domain.rule.check.FromTemporalAccessorCheck;
import dev.ward.analysis.domain.rule.check.IncompatibleArgumentTypeCheck;
import dev.ward.analysis.domain.rule.check.NonCanonicalStaticImportCheck;
import dev.ward.analysis.domain.rule.check.PrivateSecurityContractProtoAccessCheck;
import dev.ward.analysis.domain.rule.check.ProtoTruthMixedDescriptorsCheck;
import dev.ward.analysis.domain.rule.check.RestrictedApiCheck;
import dev.ward.analysis.domain.rule.check.UnnecessaryTypeArgumentCheck;
import dev.ward.analysis.domain.rule.check.WrongOneofCheck;
import java.util.Map;

/** Builds the Error Prone checks that judge a call by the facts of its target and arguments. */
final class ErrorProneCallChecks {

  private static final String MESSAGE = "message";

  private ErrorProneCallChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "deduplicate-constants",
            toml -> new DeduplicateConstantsCheck(TomlValues.required(toml, MESSAGE))),
        entry("do-not-mock", toml -> new DoNotMockCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "equals-wrong-thing",
            toml -> new EqualsWrongThingCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "extends-auto-value",
            toml -> new ExtendsAutoValueCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "incompatible-argument-type",
            toml -> new IncompatibleArgumentTypeCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "non-canonical-static-import",
            toml -> new NonCanonicalStaticImportCheck(TomlValues.required(toml, MESSAGE))),
        entry("wrong-oneof", toml -> new WrongOneofCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "format-string-annotation",
            toml -> new FormatStringAnnotationCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "format-string",
            toml ->
                new FormatStringCheck(
                    TomlValues.strings(toml, "declaring-types"),
                    TomlValues.strings(toml, "names"),
                    TomlValues.required(toml, MESSAGE))),
        entry("restricted-api", toml -> new RestrictedApiCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "private-security-contract-proto-access",
            toml ->
                new PrivateSecurityContractProtoAccessCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "proto-truth-mixed-descriptors",
            toml -> new ProtoTruthMixedDescriptorsCheck(TomlValues.required(toml, MESSAGE))),
        entry("for-override", toml -> new ForOverrideCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "from-temporal-accessor",
            toml -> new FromTemporalAccessorCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unnecessary-type-argument",
            toml -> new UnnecessaryTypeArgumentCheck(TomlValues.required(toml, MESSAGE))));
  }
}
