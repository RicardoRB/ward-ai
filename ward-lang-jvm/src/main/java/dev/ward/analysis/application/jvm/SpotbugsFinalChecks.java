package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.JavaOverridableSecurityCheck;
import dev.ward.analysis.domain.rule.check.JavaPutIfAbsentCheck;
import dev.ward.analysis.domain.rule.check.JavaSealedFieldUseCheck;
import dev.ward.analysis.domain.rule.check.JavaSerialConstructorCheck;
import dev.ward.analysis.domain.rule.check.JavaStaticInitOrderCheck;
import dev.ward.analysis.domain.rule.check.JavaStaticWriteCheck;
import dev.ward.analysis.domain.rule.check.JavaSyncAccessorCheck;
import dev.ward.analysis.domain.rule.check.JavaTypeRangeCheck;
import dev.ward.analysis.domain.rule.check.JavaUninitializedFieldCheck;
import dev.ward.analysis.domain.rule.check.JavaUselessVoidMethodCheck;
import dev.ward.analysis.domain.rule.check.JavaValueRangeCheck;
import dev.ward.analysis.domain.rule.check.JavaWaitContextCheck;
import java.util.Locale;
import java.util.Map;

/** The checks of the last SpotBugs ports for Java, which read classes, locks and field use. */
final class SpotbugsFinalChecks {

  private static final String MESSAGE = "message";
  private static final String MODE = "mode";

  private SpotbugsFinalChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "serial-constructor",
            toml ->
                new JavaSerialConstructorCheck(
                    "externalizable".equals(TomlValues.required(toml, MODE)),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "static-init-order",
            toml -> new JavaStaticInitOrderCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "static-write-from-instance",
            toml ->
                new JavaStaticWriteCheck(
                    "locked".equals(TomlValues.required(toml, MODE)),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "sync-set-unsync-get",
            toml -> new JavaSyncAccessorCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "wait-context",
            toml ->
                new JavaWaitContextCheck(
                    JavaWaitContextCheck.Mode.valueOf(
                        TomlValues.required(toml, MODE).toUpperCase(Locale.ROOT).replace('-', '_')),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "put-if-absent-ignored",
            toml -> new JavaPutIfAbsentCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "useless-void-method",
            toml -> new JavaUselessVoidMethodCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "value-range-condition",
            toml -> new JavaValueRangeCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "type-range-condition",
            toml -> new JavaTypeRangeCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "overridable-security-check",
            toml -> new JavaOverridableSecurityCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "sealed-field-use",
            toml ->
                new JavaSealedFieldUseCheck(
                    JavaSealedFieldUseCheck.Mode.valueOf(
                        TomlValues.required(toml, MODE).toUpperCase(Locale.ROOT)),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "uninitialized-field",
            toml -> new JavaUninitializedFieldCheck(TomlValues.required(toml, MESSAGE))));
  }
}
