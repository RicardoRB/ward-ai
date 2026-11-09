package dev.ward.analysis.application.python;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.CallSignatureCheck;
import dev.ward.analysis.domain.rule.check.ClassHierarchyCheck;
import dev.ward.analysis.domain.rule.check.ClassLimitCheck;
import dev.ward.analysis.domain.rule.check.IndexRangeCheck;
import dev.ward.analysis.domain.rule.check.MethodBodyCheck;
import dev.ward.analysis.domain.rule.check.PatternCountCheck;
import dev.ward.analysis.domain.rule.check.RaiseNonExceptionCheck;
import dev.ward.analysis.domain.rule.check.ReimportedCheck;
import dev.ward.analysis.domain.rule.check.RepeatedKeywordCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.ShadowedImportCheck;
import dev.ward.analysis.domain.rule.check.SimplifiableConditionCheck;
import dev.ward.analysis.domain.rule.check.StatementListCheck;
import dev.ward.analysis.domain.rule.check.UnpackingCountCheck;
import dev.ward.analysis.domain.rule.check.UnusedNameCheck;
import java.util.Locale;
import java.util.Map;
import org.tomlj.TomlParseResult;

/**
 * The declarative checks that read the shape of a Python statement, call, class or import: the
 * checks of the Pylint rows of part C. Each check takes its {@code problem} from the rule body.
 */
final class PythonShapeChecks {

  private static final String MESSAGE = "message";
  private static final String PROBLEM = "problem";
  private static final String MAX = "max";
  private static final String MIN = "min";

  private PythonShapeChecks() {}

  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry("call-signature", PythonShapeChecks::callSignature),
        entry("class-hierarchy", PythonShapeChecks::classHierarchy),
        entry("method-body", PythonShapeChecks::methodBody),
        entry("reimported", toml -> new ReimportedCheck(TomlValues.required(toml, MESSAGE))),
        entry("class-limit", PythonShapeChecks::classLimit),
        entry("index-range", toml -> new IndexRangeCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "shadowed-import", toml -> new ShadowedImportCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "simplifiable-condition",
            toml -> new SimplifiableConditionCheck(TomlValues.required(toml, MESSAGE))),
        entry("pattern-count", toml -> new PatternCountCheck(TomlValues.required(toml, MESSAGE))),
        entry("unpacking-count", PythonShapeChecks::unpackingCount),
        entry("unused-name", PythonShapeChecks::unusedName),
        entry("statement-list", PythonShapeChecks::statementList),
        entry(
            "raise-non-exception",
            toml -> new RaiseNonExceptionCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "repeated-keyword",
            toml -> new RepeatedKeywordCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static <E extends Enum<E>> E problem(final TomlParseResult toml, final Class<E> type) {
    return Enum.valueOf(
        type, TomlValues.required(toml, PROBLEM).toUpperCase(Locale.ROOT).replace('-', '_'));
  }

  private static RuleCheck callSignature(final TomlParseResult toml) {
    return new CallSignatureCheck(
        problem(toml, CallSignatureCheck.Problem.class), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck classHierarchy(final TomlParseResult toml) {
    return new ClassHierarchyCheck(
        problem(toml, ClassHierarchyCheck.Problem.class), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck methodBody(final TomlParseResult toml) {
    return new MethodBodyCheck(
        problem(toml, MethodBodyCheck.Problem.class), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck unusedName(final TomlParseResult toml) {
    return new UnusedNameCheck(
        problem(toml, UnusedNameCheck.Problem.class), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck statementList(final TomlParseResult toml) {
    return new StatementListCheck(
        problem(toml, StatementListCheck.Problem.class), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck unpackingCount(final TomlParseResult toml) {
    return new UnpackingCountCheck(
        problem(toml, UnpackingCountCheck.Problem.class), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck classLimit(final TomlParseResult toml) {
    final boolean minimum = toml.contains(MIN);
    return new ClassLimitCheck(
        problem(toml, ClassLimitCheck.Problem.class),
        TomlValues.integer(toml, minimum ? MIN : MAX),
        TomlValues.required(toml, MESSAGE));
  }
}
