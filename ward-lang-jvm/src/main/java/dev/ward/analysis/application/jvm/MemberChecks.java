package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.InheritedSignatureCheck;
import dev.ward.analysis.domain.rule.check.MustInvokeSuperCheck;
import dev.ward.analysis.domain.rule.check.RelatedTypesCheck;
import dev.ward.analysis.domain.rule.check.UnhashableKeyCheck;
import dev.ward.analysis.domain.rule.check.UnrelatedArgumentCheck;
import java.util.Locale;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** The checks of the rules that read the member facts, the shapes and the overridden methods. */
final class MemberChecks {

  private MemberChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "inherited-signature",
            toml ->
                new InheritedSignatureCheck(
                    InheritedSignatureCheck.Mode.valueOf(constant(toml, "mode")),
                    TomlValues.required(toml, "message"))),
        entry(
            "unhashable-key",
            toml ->
                new UnhashableKeyCheck(
                    UnhashableKeyCheck.Use.valueOf(constant(toml, "use")),
                    TomlValues.required(toml, "message"))),
        entry(
            "related-types",
            toml ->
                new RelatedTypesCheck(
                    RelatedTypesCheck.Comparison.valueOf(constant(toml, "comparison")),
                    RelatedTypesCheck.Pair.valueOf(constant(toml, "pair")),
                    TomlValues.required(toml, "message"))),
        entry(
            "unrelated-argument",
            toml -> new UnrelatedArgumentCheck(TomlValues.required(toml, "message"))),
        entry(
            "must-invoke-super",
            toml -> new MustInvokeSuperCheck(TomlValues.required(toml, "message"))));
  }

  private static String constant(final TomlParseResult toml, final String key) {
    return TomlValues.required(toml, key).replace('-', '_').toUpperCase(Locale.ROOT);
  }
}
