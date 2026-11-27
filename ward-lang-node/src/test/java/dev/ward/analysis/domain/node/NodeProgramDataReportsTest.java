package dev.ward.analysis.domain.node;

import static dev.ward.analysis.domain.node.NodeProgramDataFixtures.MAIN;
import static dev.ward.analysis.domain.node.NodeProgramDataFixtures.markup;
import static dev.ward.analysis.domain.node.NodeProgramDataFixtures.steps;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** What stays reported: data that code can change, and functions called from unseen places. */
class NodeProgramDataReportsTest {
  @Test
  void oneCallerPassingRequestDataReports() {
    assertThat(markup(MAIN + "function other(req) { show({ text: req.query.q }) }\n", steps()))
        .hasSize(1);
  }

  @Test
  void aConstantMutatedWithRequestDataReports() {
    assertThat(markup(MAIN + "Steps.hints.push({ text: req.query.q })\n", steps())).hasSize(1);
    assertThat(markup(MAIN + "Steps.hints[0].text = req.query.q\n", steps())).hasSize(1);
    assertThat(markup(MAIN + "const first = all[0]; first.name = req.q\n", steps())).hasSize(1);
  }

  @Test
  void aConstantMutatedInItsOwnFileReports() {
    assertThat(
        markup(
            MAIN,
            Map.of("src/steps.ts", NodeProgramDataFixtures.HINTS + "Steps.hints[0].text = req.q\n")
        )
    )
        .hasSize(1);
  }

  @Test
  void aTextThatIsNotAConstantReports() {
    final String steps = NodeProgramDataFixtures.HINTS.replace("text: 'one'", "text: fetchText()");
    assertThat(markup(MAIN, Map.of("src/steps.ts", steps))).hasSize(1);
  }

  @Test
  void anUnknownImportReports() {
    assertThat(markup(MAIN, Map.of())).hasSize(1);
    assertThat(markup(MAIN.replace("snarkdown", "markdown-it"), steps())).hasSize(1);
  }

  @Test
  void aFunctionThatReadsItsArgumentsObjectKeepsItsParametersTainted() {
    final String main =
        MAIN.replace(
            "el.innerHTML = md(hint.text)",
            "el.innerHTML = md(hint.text); arguments[0].text = req.q"
        );
    assertThat(markup(main, steps())).hasSize(1);
  }

  @Test
  void aScopeThatEvalCanReachIsNotFollowed() {
    assertThat(markup(MAIN + "eval(code)\n", steps())).hasSize(1);
  }

  @Test
  void aTextBuiltFromConstantsAndAMethodThatChangesTheDataInAnotherFileIsReported() {
    final String steps = NodeProgramDataFixtures.HINTS.replace(
        "go: () => 1",
        "go() { this.text = req.q }"
    );
    assertThat(markup(MAIN, Map.of("src/steps.ts", steps))).hasSize(1);
  }
}
