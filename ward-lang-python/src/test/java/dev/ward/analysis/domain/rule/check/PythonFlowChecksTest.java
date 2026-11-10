package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.PythonControlFlow;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The Python flow checks, run over the control-flow graphs the Python lowering builds. */
final class PythonFlowChecksTest {

  private static final int LINE_2 = 2;
  private static final int LINE_3 = 3;
  private static final int LINE_4 = 4;
  private static final int LINE_5 = 5;
  private static final int LINE_6 = 6;

  private static final String AFTER_TWO = "def f():\n    print(x)\n    x = 1\n";
  private static final String OPTIONAL_BRANCH =
      "def f(c):\n    if c:\n        x = 1\n    return x\n";
  private static final String BOTH_BRANCHES =
      "def f(c):\n    if c:\n        x = 1\n    else:\n        x = 2\n    return x\n";
  private static final String TRY_EXCEPT =
      "def f():\n    try:\n        x = g()\n    except ValueError:\n        pass\n    return x\n";
  private static final String LOOP_ITEMS =
      "def f(items):\n    for x in items:\n        pass\n    return x\n";
  private static final String LOOP_RANGE =
      "def f():\n    for x in range(3):\n        pass\n    return x\n";
  private static final String PLAIN_AND_LOOP =
      "def f(items, c):\n    if c:\n        x = 1\n    for x in items:\n        pass\n"
          + "    return x\n";
  private static final String MATCH_CATCH_ALL =
      "def f(v):\n    match v:\n        case 1:\n            x = 1\n"
          + "        case _:\n            x = 2\n    return x\n";
  private static final String MATCH_NO_CATCH_ALL =
      "def f(v):\n    match v:\n        case 1:\n            x = 1\n    return x\n";
  private static final String WHILE_ELSE =
      "def f(c):\n    while c:\n        x = 1\n        break\n    else:\n"
          + "        x = 2\n    return x\n";
  private static final String TRY_FINALLY =
      "def f():\n    try:\n        x = g()\n    finally:\n        pass\n    return x\n";
  private static final String WITH_ITEM =
      "def f(p):\n    with open(p) as fh:\n        data = fh.read()\n    return data\n";
  private static final String WALRUS =
      "def f(v):\n    if (n := len(v)) > 1:\n        return n\n    return 0\n";

  /** The used-before-assignment problems, each with the lines it reports for one source. */
  static Stream<Arguments> bindingCases() {
    return Stream.of(
        Arguments.of(UsedBeforeAssignmentCheck.Problem.NEVER_BOUND, AFTER_TWO, List.of(LINE_2)),
        Arguments.of(
            UsedBeforeAssignmentCheck.Problem.POSSIBLY_UNBOUND, OPTIONAL_BRANCH, List.of(LINE_4)),
        Arguments.of(UsedBeforeAssignmentCheck.Problem.NEVER_BOUND, OPTIONAL_BRANCH, List.of()),
        Arguments.of(UsedBeforeAssignmentCheck.Problem.POSSIBLY_UNBOUND, BOTH_BRANCHES, List.of()),
        Arguments.of(UsedBeforeAssignmentCheck.Problem.NEVER_BOUND, BOTH_BRANCHES, List.of()),
        Arguments.of(
            UsedBeforeAssignmentCheck.Problem.NEVER_BOUND, "def f(a):\n    return a\n", List.of()),
        Arguments.of(
            UsedBeforeAssignmentCheck.Problem.NEVER_BOUND,
            "def f():\n    x += 1\n",
            List.of(LINE_2)),
        Arguments.of(
            UsedBeforeAssignmentCheck.Problem.NEVER_BOUND,
            "def f():\n    global x\n    print(x)\n    x = 1\n",
            List.of()),
        Arguments.of(
            UsedBeforeAssignmentCheck.Problem.POSSIBLY_UNBOUND, TRY_EXCEPT, List.of(LINE_6)),
        Arguments.of(UsedBeforeAssignmentCheck.Problem.LOOP_VARIABLE, LOOP_ITEMS, List.of(LINE_4)),
        Arguments.of(UsedBeforeAssignmentCheck.Problem.POSSIBLY_UNBOUND, LOOP_ITEMS, List.of()),
        Arguments.of(UsedBeforeAssignmentCheck.Problem.LOOP_VARIABLE, LOOP_RANGE, List.of()),
        Arguments.of(UsedBeforeAssignmentCheck.Problem.POSSIBLY_UNBOUND, LOOP_RANGE, List.of()),
        Arguments.of(
            UsedBeforeAssignmentCheck.Problem.POSSIBLY_UNBOUND, PLAIN_AND_LOOP, List.of(LINE_6)),
        Arguments.of(UsedBeforeAssignmentCheck.Problem.LOOP_VARIABLE, PLAIN_AND_LOOP, List.of()),
        Arguments.of(
            UsedBeforeAssignmentCheck.Problem.POSSIBLY_UNBOUND, MATCH_CATCH_ALL, List.of()),
        Arguments.of(
            UsedBeforeAssignmentCheck.Problem.POSSIBLY_UNBOUND,
            MATCH_NO_CATCH_ALL,
            List.of(LINE_5)),
        Arguments.of(UsedBeforeAssignmentCheck.Problem.POSSIBLY_UNBOUND, WHILE_ELSE, List.of()),
        Arguments.of(UsedBeforeAssignmentCheck.Problem.POSSIBLY_UNBOUND, TRY_FINALLY, List.of()),
        Arguments.of(UsedBeforeAssignmentCheck.Problem.NEVER_BOUND, TRY_FINALLY, List.of()),
        Arguments.of(
            UsedBeforeAssignmentCheck.Problem.NEVER_BOUND,
            "def f():\n    x = 1\n    del x\n    return x\n",
            List.of(LINE_4)),
        Arguments.of(UsedBeforeAssignmentCheck.Problem.NEVER_BOUND, WALRUS, List.of()),
        Arguments.of(UsedBeforeAssignmentCheck.Problem.POSSIBLY_UNBOUND, WALRUS, List.of()),
        Arguments.of(UsedBeforeAssignmentCheck.Problem.POSSIBLY_UNBOUND, WITH_ITEM, List.of()),
        Arguments.of(UsedBeforeAssignmentCheck.Problem.NEVER_BOUND, WITH_ITEM, List.of()),
        Arguments.of(
            UsedBeforeAssignmentCheck.Problem.NEVER_BOUND,
            "def f():\n    x: int\n    return x\n",
            List.of(LINE_3)),
        Arguments.of(
            UsedBeforeAssignmentCheck.Problem.NEVER_BOUND,
            "from m import *\nprint(y)\ny = 1\n",
            List.of()));
  }

  @ParameterizedTest
  @MethodSource("bindingCases")
  void aBindingProblemIsReportedOnExactlyTheExpectedLines(
      final UsedBeforeAssignmentCheck.Problem problem,
      final String source,
      final List<Integer> expected) {
    assertThat(lines(new UsedBeforeAssignmentCheck(problem, "m"), source)).isEqualTo(expected);
  }

  /** The statements after a jump, which the sibling check reports. */
  static Stream<Arguments> unreachableCases() {
    return Stream.of(
        Arguments.of("def f():\n    return 1\n    y = 2\n", List.of(LINE_3)),
        Arguments.of("def f():\n    while True:\n        pass\n    z = 1\n", List.of()),
        Arguments.of(
            "def f(xs):\n    for x in xs:\n        break\n        z = 1\n", List.of(LINE_4)),
        Arguments.of("def g():\n    return 1\n    yield 2\n    y = 3\n", List.of(LINE_4)),
        Arguments.of("def f(c):\n    while c:\n        pass\n    z = 1\n", List.of()),
        Arguments.of("def f():\n    with g():\n        return 1\n    z = 2\n", List.of()));
  }

  @ParameterizedTest
  @MethodSource("unreachableCases")
  void aJumpReportsTheStatementThatFollowsItInTheSameBlock(
      final String source, final List<Integer> expected) {
    assertThat(lines(new PythonUnreachableStatementCheck("m"), source)).isEqualTo(expected);
  }

  @Test
  void aFunctionFlowViewHasOneGraphPerBodyAndTheModule() {
    final SyntaxNode tree = ParsedPython.tree(OPTIONAL_BRANCH);
    final FlowView flow =
        new FlowView(
            new PythonControlFlow()
                .lower(new SourceUnit("module.py", AnalysisLanguage.PYTHON, OPTIONAL_BRANCH), tree)
                .graphs());
    assertThat(flow.graphs()).hasSize(2);
  }

  /** The line of each finding of {@code check} over {@code source}, in order. */
  private static List<Integer> lines(final RuleCheck check, final String source) {
    final SyntaxNode tree = ParsedPython.tree(source);
    final FlowView flow =
        new FlowView(
            new PythonControlFlow()
                .lower(new SourceUnit("module.py", AnalysisLanguage.PYTHON, source), tree)
                .graphs());
    return check.matches(tree, flow).stream().map(match -> line(match, source)).toList();
  }

  private static int line(final RuleMatch match, final String source) {
    final String before = source.substring(0, match.span().startOffset());
    return (int) before.chars().filter(c -> c == '\n').count() + 1;
  }
}
