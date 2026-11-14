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

/** The flow sites that the Python lowering records for the taint rules. */
class PythonFlowSitesTest {

  private static List<FlowSite> sites(final String source, final String body) {
    final SourceUnit unit = new SourceUnit("m.py", AnalysisLanguage.PYTHON, source);
    final ControlFlowGraph graph =
        new PythonControlFlow()
            .lower(unit, new AntlrLanguageFrontend().parse(unit).tree()).graphs().stream()
                .filter(candidate -> candidate.body().equals(body))
                .findFirst()
                .orElseThrow();
    return graph.blocks().stream().map(BasicBlock::sites).flatMap(List::stream).toList();
  }

  @Test
  void aliasedModuleCallHasTheModuleAsReceiver() {
    final List<FlowSite> found =
        sites("import subprocess as sp\ndef f(cmd):\n    sp.run(cmd, shell=True)\n", "f");
    final FlowSite call = found.get(0);
    assertThat(call.kind()).isEqualTo(FlowSiteKind.CALL);
    assertThat(call.name()).isEqualTo("run");
    assertThat(call.receiverType()).contains("subprocess");
    assertThat(call.arguments()).hasSize(2);
  }

  @Test
  void fromImportedNameIsCalledOnItsModule() {
    final FlowSite call = sites("from os import system as s\ndef f(c):\n    s(c)\n", "f").get(0);
    assertThat(call.name()).isEqualTo("system");
    assertThat(call.receiverType()).contains("os");
  }

  @Test
  void requestMemberIsReadBeforeTheCallThatUsesIt() {
    final List<FlowSite> found = sites("def f():\n    q = request.args.get('q')\n", "f");
    assertThat(found)
        .extracting(FlowSite::kind)
        .containsExactly(FlowSiteKind.MEMBER_READ, FlowSiteKind.CALL);
    assertThat(found.get(0).name()).isEqualTo("args");
    assertThat(found.get(0).qualifier()).isEqualTo("request");
    assertThat(found.get(1).qualifier()).isEqualTo("request.args");
  }

  @Test
  void subscriptOfMemberIsAMemberRead() {
    final List<FlowSite> found = sites("def f():\n    q = request.form['q']\n", "f");
    assertThat(found).hasSize(1);
    assertThat(found.get(0).name()).isEqualTo("form");
  }

  @Test
  void keywordAndStarArgumentsAreValues() {
    final FlowSite call = sites("def f(a, b):\n    g(x=a, *b)\n", "f").get(0);
    assertThat(call.arguments()).hasSize(2);
  }

  @Test
  void decoratedHandlerParametersCarryTheDecoratorAndDefaultCall() {
    final List<FlowSite> found =
        sites("@app.get('/x')\ndef f(self, q: str, n=Query(1), request=None):\n    pass\n", "f");
    assertThat(found).extracting(FlowSite::name).containsExactly("q", "n");
    assertThat(found.get(0).annotations()).containsExactly("@get");
    assertThat(found.get(1).annotations()).containsExactly("@get", "Query");
  }

  @Test
  void undecoratedParametersAreNotSources() {
    assertThat(sites("def f(q):\n    pass\n", "f")).isEmpty();
  }

  @Test
  void nestedFunctionsAndLambdasAreNotEntered() {
    assertThat(sites("def f():\n    g = lambda: h()\n    def k():\n        z()\n", "f")).isEmpty();
  }

  @Test
  void relativeImportsAreNotLibraryNames() {
    final FlowSite call = sites("from .util import run\ndef f(c):\n    run(c)\n", "f").get(0);
    assertThat(call.receiverType()).isEmpty();
  }
}
