package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AnnotationReflectionCheck;
import dev.ward.analysis.domain.rule.check.AppletConstructorCheck;
import dev.ward.analysis.domain.rule.check.ArrayRangeCheck;
import dev.ward.analysis.domain.rule.check.ByteAddCheck;
import dev.ward.analysis.domain.rule.check.CastedArgumentCheck;
import dev.ward.analysis.domain.rule.check.StringRangeCheck;
import dev.ward.analysis.domain.rule.check.TimeFromIntCheck;
import dev.ward.analysis.domain.rule.check.UnusedClassLiteralCheck;
import java.util.Locale;
import java.util.Map;

/** The checks of single SpotBugs correctness rules that need a check of their own. */
final class OneOffChecks {

  private static final String MESSAGE = "message";

  private OneOffChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "annotation-reflection",
            toml -> new AnnotationReflectionCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unused-class-literal",
            toml -> new UnusedClassLiteralCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "applet-constructor",
            toml -> new AppletConstructorCheck(TomlValues.required(toml, MESSAGE))),
        entry("byte-add", toml -> new ByteAddCheck(TomlValues.required(toml, MESSAGE))),
        entry("time-from-int", toml -> new TimeFromIntCheck(TomlValues.required(toml, MESSAGE))),
        entry("string-range", toml -> new StringRangeCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "casted-argument",
            toml ->
                new CastedArgumentCheck(
                    TomlValues.required(toml, "receiver"),
                    TomlValues.required(toml, "method"),
                    TomlValues.required(toml, "cast"),
                    TomlValues.strings(toml, "operand-types"),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "array-range",
            toml ->
                new ArrayRangeCheck(
                    ArrayRangeCheck.Mode.valueOf(
                        TomlValues.required(toml, "mode").toUpperCase(Locale.ROOT)),
                    TomlValues.required(toml, MESSAGE))));
  }
}
