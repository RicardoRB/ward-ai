package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.PythonControlFlow;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Python flow checks, run over the control-flow graphs the Python lowering builds. */
final class PythonFlowChecksTest {

  private static final int LINE_2 = 2;
  private static final int LINE_3 = 3;
  private static final int LINE_4 = 4;
  private static final int LINE_5 = 5;
  private static final int LINE_6 = 6;

  private static final UsedBeforeAssignmentCheck NEVER =
      new UsedBeforeAssignmentCheck(UsedBeforeAssignmentCheck.Problem.NEVER_BOUND, "m");
  private static final UsedBeforeAssignmentCheck POSSIBLY =
      new UsedBeforeAssignmentCheck(UsedBeforeAssignmentCheck.Problem.POSSIBLY_UNBOUND, "m");
  private static final UsedBeforeAssignmentCheck LOOP =
      new UsedBeforeAssignmentCheck(UsedBeforeAssignmentCheck.Problem.LOOP_VARIABLE, "m");
  private static final PythonUnreachableStatementCheck UNREACHABLE =
      new PythonUnreachableStatementCheck("m");

  @Test
  void aReadBeforeAnyAssignmentIsNeverBound() {
    assertThat(lines(NEVER, "def f():\n    print(x)\n    x = 1\n")).containsExactly(LINE_2);
  }

  @Test
  void aReadAfterAnAssignmentOnOneBranchIsPossiblyUnbound() {
    final String source = "def f(c):\n    if c:\n        x = 1\n    return x\n";
    assertThat(lines(POSSIBLY, source)).containsExactly(LINE_4);
    assertThat(lines(NEVER, source)).isEmpty();
  }

  @Test
  void anAssignmentOnEveryBranchBindsTheName() {
    final String source =
        "def f(c):\n    if c:\n        x = 1\n    else:\n        x = 2\n    return x\n";
    assertThat(lines(POSSIBLY, source)).isEmpty();
    assertThat(lines(NEVER, source)).isEmpty();
  }

  @Test
  void aParameterIsBoundOnEntry() {
    assertThat(lines(NEVER, "def f(a):\n    return a\n")).isEmpty();
    assertThat(lines(POSSIBLY, "def f(a):\n    return a\n")).isEmpty();
  }

  @Test
  void anAugmentedAssignmentReadsItsTargetFirst() {
    assertThat(lines(NEVER, "def f():\n    x += 1\n")).containsExactly(LINE_2);
  }

  @Test
  void aGlobalNameIsNotCheckedInTheFunction() {
    assertThat(lines(NEVER, "def f():\n    global x\n    print(x)\n    x = 1\n")).isEmpty();
  }

  @Test
  void aNameAssignedInTryAndReadAfterTheExceptIsPossiblyUnbound() {
    final String source =
        "def f():\n    try:\n        x = g()\n    except ValueError:\n        pass\n    return x\n";
    assertThat(lines(POSSIBLY, source)).containsExactly(LINE_6);
  }

  @Test
  void aTargetOfAPossiblyEmptyLoopIsALoopVariable() {
    final String source = "def f(items):\n    for x in items:\n        pass\n    return x\n";
    assertThat(lines(LOOP, source)).containsExactly(LINE_4);
    assertThat(lines(POSSIBLY, source)).isEmpty();
  }

  @Test
  void aTargetOfALoopOverARangeAlwaysBindsIt() {
    final String source = "def f():\n    for x in range(3):\n        pass\n    return x\n";
    assertThat(lines(LOOP, source)).isEmpty();
    assertThat(lines(POSSIBLY, source)).isEmpty();
  }

  @Test
  void aPlainBindingOnAnotherPathKeepsThePossiblyUnboundMistake() {
    final String source =
        "def f(items, c):\n    if c:\n        x = 1\n    for x in items:\n        pass\n"
            + "    return x\n";
    assertThat(lines(POSSIBLY, source)).containsExactly(LINE_6);
    assertThat(lines(LOOP, source)).isEmpty();
  }

  @Test
  void aMatchWithACatchAllBindsTheNameOnEveryPath() {
    final String source =
        "def f(v):\n    match v:\n        case 1:\n            x = 1\n"
            + "        case _:\n            x = 2\n    return x\n";
    assertThat(lines(POSSIBLY, source)).isEmpty();
  }

  @Test
  void aMatchWithoutACatchAllLeavesTheNameUnbound() {
    final String source =
        "def f(v):\n    match v:\n        case 1:\n            x = 1\n    return x\n";
    assertThat(lines(POSSIBLY, source)).containsExactly(LINE_5);
  }

  @Test
  void aWhileElseWithABreakBindsTheNameOnEveryExit() {
    final String source =
        "def f(c):\n    while c:\n        x = 1\n        break\n    else:\n"
            + "        x = 2\n    return x\n";
    assertThat(lines(POSSIBLY, source)).isEmpty();
  }

  @Test
  void aNameBoundInTryIsBoundAfterAFinallyOnTheNormalPath() {
    final String source =
        "def f():\n    try:\n        x = g()\n    finally:\n        pass\n    return x\n";
    assertThat(lines(POSSIBLY, source)).isEmpty();
    assertThat(lines(NEVER, source)).isEmpty();
  }

  @Test
  void aDeletedNameIsUnboundAfterTheDelete() {
    assertThat(lines(NEVER, "def f():\n    x = 1\n    del x\n    return x\n"))
        .containsExactly(LINE_4);
  }

  @Test
  void aWalrusBindsItsNameBeforeTheBranchReadsIt() {
    final String source = "def f(v):\n    if (n := len(v)) > 1:\n        return n\n    return 0\n";
    assertThat(lines(NEVER, source)).isEmpty();
    assertThat(lines(POSSIBLY, source)).isEmpty();
  }

  @Test
  void aNameBoundByAWithItemIsBoundAfterTheBlock() {
    final String source =
        "def f(p):\n    with open(p) as fh:\n        data = fh.read()\n    return data\n";
    assertThat(lines(POSSIBLY, source)).isEmpty();
    assertThat(lines(NEVER, source)).isEmpty();
  }

  @Test
  void anAnnotationWithoutAValueDoesNotBindTheName() {
    assertThat(lines(NEVER, "def f():\n    x: int\n    return x\n")).containsExactly(LINE_3);
  }

  @Test
  void aStarImportDisablesTheCheckOfTheModule() {
    assertThat(lines(NEVER, "from m import *\nprint(y)\ny = 1\n")).isEmpty();
  }

  @Test
  void aStatementAfterReturnIsUnreachable() {
    assertThat(lines(UNREACHABLE, "def f():\n    return 1\n    y = 2\n")).containsExactly(LINE_3);
  }

  @Test
  void aStatementAfterAnEndlessLoopIsNotReportedAsPylintDoes() {
    assertThat(lines(UNREACHABLE, "def f():\n    while True:\n        pass\n    z = 1\n"))
        .isEmpty();
  }

  @Test
  void aStatementAfterABreakInsideALoopIsUnreachable() {
    assertThat(lines(UNREACHABLE, "def f(xs):\n    for x in xs:\n        break\n        z = 1\n"))
        .containsExactly(LINE_4);
  }

  @Test
  void aYieldAfterAReturnIsSkippedAndTheStatementAfterItIsReported() {
    assertThat(lines(UNREACHABLE, "def g():\n    return 1\n    yield 2\n    y = 3\n"))
        .containsExactly(LINE_4);
  }

  @Test
  void aWithStatementMayContinueAfterItsBody() {
    assertThat(lines(UNREACHABLE, "def f():\n    with g():\n        return 1\n    z = 2\n"))
        .isEmpty();
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
