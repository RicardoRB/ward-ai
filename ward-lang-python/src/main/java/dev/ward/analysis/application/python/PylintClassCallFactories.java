package dev.ward.analysis.application.python;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ClassOverrideCheck;
import dev.ward.analysis.domain.rule.check.ClassProtocolCheck;
import dev.ward.analysis.domain.rule.check.ConsecutiveStatementsCheck;
import dev.ward.analysis.domain.rule.check.FirstArgumentCheck;
import dev.ward.analysis.domain.rule.check.FormatIndexCheck;
import dev.ward.analysis.domain.rule.check.KeywordArgBeforeVarargCheck;
import dev.ward.analysis.domain.rule.check.MatchPositionalCheck;
import dev.ward.analysis.domain.rule.check.ModifiedIterationCheck;
import dev.ward.analysis.domain.rule.check.NamedExprWithoutContextCheck;
import dev.ward.analysis.domain.rule.check.NonlocalCheck;
import dev.ward.analysis.domain.rule.check.OverlappingExceptCheck;
import dev.ward.analysis.domain.rule.check.SameModuleCallCheck;
import dev.ward.analysis.domain.rule.check.TryBodyLengthCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** The Pylint-derived Python checks about calls, classes, iteration and exception handling. */
final class PylintClassCallFactories {
  private static final String MESSAGE = "message";

  private PylintClassCallFactories() {}

  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "match-positional",
            toml -> new MatchPositionalCheck(TomlValues.required(toml, MESSAGE))
        ),
        entry("same-module-call", PylintClassCallFactories::sameModuleCall),
        entry("class-protocol", PylintClassCallFactories::classProtocol),
        entry("class-override", PylintClassCallFactories::classOverride),
        entry("modified-iteration", PylintClassCallFactories::modifiedIteration),
        entry("nonlocal", PylintClassCallFactories::nonlocal),
        entry("format-index", toml -> new FormatIndexCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "keyword-arg-before-vararg",
            toml -> new KeywordArgBeforeVarargCheck(TomlValues.required(toml, MESSAGE))
        ),
        entry("first-argument", toml -> new FirstArgumentCheck(TomlValues.required(toml, MESSAGE))),
        entry("consecutive-statements", PylintClassCallFactories::consecutiveStatements),
        entry(
            "try-body-length",
            toml ->
                new TryBodyLengthCheck(
                    TomlValues.integer(toml, "max-try-statements"),
                    TomlValues.required(toml, MESSAGE)
                )
        ),
        entry(
            "overlapping-except",
            toml -> new OverlappingExceptCheck(TomlValues.required(toml, MESSAGE))
        ),
        entry(
            "named-expr-without-context",
            toml -> new NamedExprWithoutContextCheck(TomlValues.required(toml, MESSAGE))
        )
    );
  }

  private static RuleCheck sameModuleCall(final TomlParseResult toml) {
    return new SameModuleCallCheck(
        problemOf(toml, SameModuleCallCheck.Problem.class),
        TomlValues.required(toml, MESSAGE)
    );
  }

  private static RuleCheck classProtocol(final TomlParseResult toml) {
    return new ClassProtocolCheck(
        problemOf(toml, ClassProtocolCheck.Problem.class),
        TomlValues.required(toml, MESSAGE)
    );
  }

  private static RuleCheck consecutiveStatements(final TomlParseResult toml) {
    return new ConsecutiveStatementsCheck(
        problemOf(toml, ConsecutiveStatementsCheck.Problem.class),
        TomlValues.required(toml, MESSAGE)
    );
  }

  private static RuleCheck classOverride(final TomlParseResult toml) {
    return new ClassOverrideCheck(
        problemOf(toml, ClassOverrideCheck.Problem.class),
        TomlValues.required(toml, MESSAGE)
    );
  }

  private static RuleCheck modifiedIteration(final TomlParseResult toml) {
    return new ModifiedIterationCheck(
        ModifiedIterationCheck.Kind.valueOf(
            TomlValues.required(toml, "kind").toUpperCase(java.util.Locale.ROOT)
        ),
        TomlValues.required(toml, MESSAGE)
    );
  }

  private static RuleCheck nonlocal(final TomlParseResult toml) {
    return new NonlocalCheck(TomlValues.required(toml, MESSAGE));
  }

  /**
   * The enum constant named by the rule's {@code problem} option, such as no-value-for-parameter.
   */
  private static <E extends Enum<E>> E problemOf(final TomlParseResult toml, final Class<E> type) {
    return Enum.valueOf(
        type,
        TomlValues.required(toml, "problem").toUpperCase(java.util.Locale.ROOT).replace('-', '_')
    );
  }
}
