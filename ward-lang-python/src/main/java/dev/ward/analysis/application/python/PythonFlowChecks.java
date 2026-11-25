package dev.ward.analysis.application.python;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.ir.Confidence;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.InconsistentReturnCheck;
import dev.ward.analysis.domain.rule.check.PythonUnreachableStatementCheck;
import dev.ward.analysis.domain.rule.check.UsedBeforeAssignmentCheck;
import dev.ward.analysis.domain.rule.check.WeakDigestCheck;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.tomlj.TomlParseResult;

/**
 * The Python checks that read the control-flow graphs of a file, such as Pylint's E0601 and E0606.
 */
final class PythonFlowChecks {
  private static final String MESSAGE = "message";
  private static final String MIN_CONFIDENCE = "min-confidence";

  private PythonFlowChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(checksByBinding()));
  }

  private static Map<String, CheckFactory> checksByBinding() {
    return Map.ofEntries(
        entry(
            "weak-digest",
            toml ->
                new WeakDigestCheck(TomlValues.required(toml, MESSAGE), minConfidence(toml))
        ),
        entry(
            "unreachable-statement",
            toml -> new PythonUnreachableStatementCheck(TomlValues.required(toml, MESSAGE))
        ),
        entry(
            "inconsistent-return-statements",
            toml -> new InconsistentReturnCheck(TomlValues.required(toml, MESSAGE))
        ),
        entry(
            "used-before-assignment",
            toml ->
                new UsedBeforeAssignmentCheck(
                    UsedBeforeAssignmentCheck.Problem.NEVER_BOUND,
                    TomlValues.required(toml, MESSAGE)
                )
        ),
        entry(
            "possibly-used-before-assignment",
            toml ->
                new UsedBeforeAssignmentCheck(
                    UsedBeforeAssignmentCheck.Problem.POSSIBLY_UNBOUND,
                    TomlValues.required(toml, MESSAGE)
                )
        ),
        entry(
            "undefined-loop-variable",
            toml ->
                new UsedBeforeAssignmentCheck(
                    UsedBeforeAssignmentCheck.Problem.LOOP_VARIABLE,
                    TomlValues.required(toml, MESSAGE)
                )
        )
    );
  }

  /** The {@code min-confidence} of a body: {@code proven} unless it says {@code possible}. */
  private static Confidence minConfidence(final TomlParseResult toml) {
    final String key =
        Optional.ofNullable(toml.getString(MIN_CONFIDENCE)).orElse(Confidence.PROVEN.key());
    return Confidence.byKey(key)
        .orElseThrow(
            () ->
                new RuleConfigurationException(
                    "'" + MIN_CONFIDENCE + "' must be proven or possible, not '" + key + "'"
                )
        );
  }
}
