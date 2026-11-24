package dev.ward.analysis.domain.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which lookups give the assigned name a clean value. */
class PythonFixedLookupsTest {
  private static final String HEAD =
      "def one():\n    return 1\n\nTABLE = {'a': one}\nOPEN = {'a': one}\nOPEN = {}\n";

  /** The writes of {@code picked} in function {@code f}; a clean lookup adds one. */
  static long writes(final String source) {
    final SourceUnit unit = new SourceUnit("m.py", AnalysisLanguage.PYTHON, source);
    final ControlFlowGraph graph =
        new PythonControlFlow()
            .lower(unit, new AntlrLanguageFrontend().parse(unit).tree()).graphs().stream()
            .filter(candidate -> "f".equals(candidate.body()))
            .findFirst()
            .orElseThrow();
    return graph.blocks().stream()
        .map(BasicBlock::accesses)
        .flatMap(List::stream)
        .filter(access -> "picked".equals(access.name()) && access.kind() == AccessKind.WRITE)
        .count();
  }

  @Test
  void aGetOnAFixedTableCleansTheName() {
    assertThat(writes(HEAD + "def f(key):\n    picked = TABLE.get(key)\n    use(picked)\n"))
        .isEqualTo(2);
  }

  @Test
  void anIndexOfAFixedTableCleansTheName() {
    assertThat(writes(HEAD + "def f(key):\n    picked = TABLE[key]\n    use(picked)\n"))
        .isEqualTo(2);
  }

  @Test
  void aTableThatTheFileRebindsDoesNot() {
    assertThat(writes(HEAD + "def f(key):\n    picked = OPEN.get(key)\n    use(picked)\n"))
        .isEqualTo(1);
  }

  @Test
  void aLookupThatIsPartOfALargerExpressionDoesNot() {
    assertThat(writes(HEAD + "def f(key):\n    picked = TABLE.get(key) + key\n    use(picked)\n"))
        .isEqualTo(1);
  }

  @Test
  void aTableThatIsAParameterDoesNot() {
    assertThat(writes(HEAD + "def f(key, TABLE):\n    picked = TABLE.get(key)\n    use(picked)\n"))
        .isEqualTo(1);
  }

  @Test
  void aTableOfNamesThatAreNotDefinitionsIsNotFixed() {
    assertThat(
        writes("TABLE = {'a': other}\ndef f(key):\n    picked = TABLE.get(key)\n    use(picked)\n")
    )
        .isEqualTo(1);
  }

  @Test
  void aGetWithADefaultDoesNot() {
    assertThat(writes(HEAD + "def f(key):\n    picked = TABLE.get(key, key)\n    use(picked)\n"))
        .isEqualTo(1);
  }

  @Test
  void aGetWithOneArgumentThatIsACallStillCleansTheName() {
    assertThat(writes(HEAD + "def f(key):\n    picked = TABLE.get(str(key, 1))\n    use(picked)\n"))
        .isEqualTo(2);
  }
}
