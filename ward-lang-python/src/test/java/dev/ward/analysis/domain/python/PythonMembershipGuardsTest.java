package dev.ward.analysis.domain.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import org.junit.jupiter.api.Test;

/** Which membership tests give a name a clean value, and which collections count. */
class PythonMembershipGuardsTest {
  private static final String HEAD = "ALLOWED = ('a', 'b')\n";

  /** The writes of {@code name} in function {@code f}; a guard adds one. */
  private static long writes(final String source) {
    final SourceUnit unit = new SourceUnit("m.py", AnalysisLanguage.PYTHON, source);
    final ControlFlowGraph graph =
        new PythonControlFlow()
            .lower(unit, new AntlrLanguageFrontend().parse(unit).tree()).graphs().stream()
            .filter(candidate -> "f".equals(candidate.body()))
            .findFirst()
            .orElseThrow();
    return graph.blocks().stream()
        .map(BasicBlock::accesses)
        .flatMap(java.util.List::stream)
        .filter(access -> "name".equals(access.name()) && access.kind() == AccessKind.WRITE)
        .count();
  }

  @Test
  void aFixedCollectionCleansTheNameInsideTheBranch() {
    assertThat(writes(HEAD + "def f(name):\n    if name in ALLOWED:\n        use(name)\n"))
        .isEqualTo(1);
  }

  @Test
  void aLiteralOnTheRightCleansTheName() {
    assertThat(writes("def f(name):\n    if name in {'a', 'b'}:\n        use(name)\n"))
        .isEqualTo(1);
  }

  @Test
  void anExitingNotInCleansTheNameAfterTheStatement() {
    assertThat(
        writes(HEAD + "def f(name):\n    if name not in ALLOWED:\n        return\n    use(name)\n")
    )
        .isEqualTo(1);
  }

  @Test
  void aNotInThatDoesNotExitCleansNothing() {
    assertThat(
        writes(HEAD + "def f(name):\n    if name not in ALLOWED:\n        log()\n    use(name)\n")
    )
        .isZero();
  }

  @Test
  void aParameterCollectionCleansNothing() {
    assertThat(writes("def f(name, allowed):\n    if name in allowed:\n        use(name)\n"))
        .isZero();
  }

  @Test
  void aReboundCollectionCleansNothing() {
    assertThat(
        writes(
            HEAD + "ALLOWED = load()\ndef f(name):\n    if name in ALLOWED:\n        use(name)\n"
        )
    )
        .isZero();
  }

  @Test
  void aCollectionOfRequestValuesCleansNothing() {
    assertThat(
        writes(
            "def f(name):\n    names = request.args.getlist('n')\n"
                + "    if name in names:\n        use(name)\n"
        )
    )
        .isZero();
  }

  @Test
  void theNamesOfADirectoryCleanTheName() {
    assertThat(
        writes(
            "def f(name):\n    names = [e for e in os.listdir('d')]\n"
                + "    if name in names:\n        use(name)\n"
        )
    )
        .isEqualTo(1);
  }

  @Test
  void anExitThroughAbortCountsWhenFlaskImportsIt() {
    assertThat(
        writes(
            HEAD + "from flask import abort\ndef f(name):\n    if name not in ALLOWED:\n"
                + "        abort(404)\n    use(name)\n"
        )
    )
        .isEqualTo(1);
  }
}
