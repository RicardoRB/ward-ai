package dev.ward.analysis.domain.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A list or tuple given to a subprocess function without a shell stands for no command text. */
class PythonArgumentVectorSitesTest {
  @Test
  void subprocessArgumentVectorWithoutShellStandsForNoCommandText() {
    final String source =
        "import subprocess\nfrom subprocess import call\ndef f(a, b):\n"
            + "    subprocess.run(['ls', a])\n"
            + "    call(('grep', b, '.'), shell=False)\n"
            + "    subprocess.run([a], shell=True)\n"
            + "    subprocess.run(a)\n"
            + "    subprocess.run((a))\n"
            + "    other.run([a])\n";
    final List<FlowSite> calls =
        PythonFlowSitesTest.sites(source, "f").stream().filter(
            site -> site.kind() == FlowSiteKind.CALL
        ).toList();

    assertThat(calls)
        .extracting(
            call -> call.arguments().get(0).startOffset() == call.arguments().get(0)
                .endOffset()
        )
        .containsExactly(true, true, false, false, false, false);
  }
}
