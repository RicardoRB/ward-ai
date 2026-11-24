package dev.ward.analysis.domain.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the lowering marks about validators and about the values that guards proved. */
class PythonHostValidatorsTest {
  private static final String HEAD =
      "from urllib.parse import urlparse\nfrom flask import request, abort\n\n\n";

  private static List<FlowSite> sites(final String source, final String body) {
    final SourceUnit unit = new SourceUnit("m.py", AnalysisLanguage.PYTHON, HEAD + source);
    final ControlFlowGraph graph =
        new PythonControlFlow()
            .lower(unit, new AntlrLanguageFrontend().parse(unit).tree()).graphs().stream()
            .filter(candidate -> ("m#" + body).equals(candidate.body()))
            .findFirst()
            .orElseThrow();
    return graph.blocks().stream().map(BasicBlock::sites).flatMap(List::stream).toList();
  }

  private static List<String> marks(final String source, final String body) {
    return sites(source, body).stream()
        .filter(site -> "<validated-return>".equals(site.name()))
        .map(site -> site.qualifier() + ":" + site.annotations().get(1))
        .toList();
  }

  private static List<String> uses(final String source, final String body) {
    return sites(source, body).stream()
        .filter(site -> site.kind() == FlowSiteKind.VALIDATED)
        .filter(site -> site.name().startsWith("host:"))
        .map(FlowSite::name)
        .toList();
  }

  @Test
  void aRejectionOfAHostMarksTheReturnsThatFollowIt() {
    final String source =
        "def check(url):\n    p = urlparse(url)\n    if p.netloc:\n        return False\n"
            + "    return p.path.startswith('/')\n";

    final String compared =
        "def compared(url):\n    p = urlparse(url)\n"
            + "    return p.scheme == 'a' and p.netloc == request.host\n";
    final String chained = "def chained(url):\n    if urlparse(url).netloc:\n        return False\n    return True\n";

    assertThat(marks(source, "check")).containsExactly("url:safe", "url:safe");
    assertThat(marks(compared, "compared")).containsExactly("url:safe");
    assertThat(marks(chained, "chained")).containsExactly("url:safe", "url:safe");
  }

  @Test
  void aReturnThatCallsAnotherFunctionOnTheValueIsMarkedAsCalls() {
    final String source = "def check(url):\n    return other(url) and other(url.strip())\n";

    assertThat(marks(source, "check")).containsExactly("url:calls");
  }

  @Test
  void aFunctionThatCanReturnTrueWithoutACheckMarksNothing() {
    assertThat(marks("def check(url):\n    return url.startswith('/')\n", "check")).isEmpty();
    assertThat(
        marks("def check(url):\n    if url:\n        return True\n    return False\n", "check")
    )
        .isEmpty();
    assertThat(marks("def check(url):\n    return a(url) or b(url)\n", "check")).isEmpty();
  }

  @Test
  void aGuardMakesUsesOfTheCheckedValueInsideTheBranch() {
    final String source =
        "def f():\n    t = request.args.get('n')\n    if t and check(t):\n        go(t)\n"
            + "def check(url):\n    return False\n";

    assertThat(uses(source, "f")).containsExactly("host:check@0");
  }

  @Test
  void aNegatedGuardThatExitsMakesUsesInTheStatementsThatFollow() {
    final String source =
        "def f():\n    t = request.args.get('n')\n    if not check(t):\n        abort(400)\n"
            + "    go(t)\ndef check(url):\n    return False\n";

    assertThat(uses(source, "f")).containsExactly("host:check@0");
  }

  @Test
  void aGuardThatDoesNotExitOrWhoseValueIsAssignedAgainMakesNone() {
    final String open =
        "def f():\n    t = request.args.get('n')\n    if not check(t):\n        pass\n    go(t)\n"
            + "def check(url):\n    return False\n";
    final String changed =
        "def f():\n    t = request.args.get('n')\n    if check(t):\n        t = other()\n        go(t)\n"
            + "def check(url):\n    return False\n";

    assertThat(uses(open, "f")).isEmpty();
    assertThat(uses(changed, "f")).isEmpty();
  }

  @Test
  void theElseBranchOfANegatedGuardRelies() {
    final String source =
        "def f():\n    t = request.args.get('n')\n    if not check(t):\n        pass\n    else:\n"
            + "        go(t)\ndef check(url):\n    return False\n";

    assertThat(uses(source, "f")).containsExactly("host:check@0");
  }
}
