package dev.ward.analysis.domain.node;

import static dev.ward.analysis.domain.node.NodeProgramDataFixtures.MAIN;
import static dev.ward.analysis.domain.node.NodeProgramDataFixtures.markup;
import static dev.ward.analysis.domain.node.NodeProgramDataFixtures.steps;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Program data: constants reach a sink through names, parameters, loops and imports. */
class NodeProgramDataTest {
  @Test
  void aParameterOfAPrivateFunctionThatEveryCallerFeedsFromConstantsIsData() {
    assertThat(markup(MAIN, Map.of("src/steps.ts", NodeProgramDataFixtures.HINTS))).isEmpty();
  }

  @Test
  void anExportedFunctionKeepsItsParametersTainted() {
    assertThat(markup(MAIN.replace("function show", "export function show"), steps())).hasSize(1);
    assertThat(markup(MAIN + "export { show }\n", steps())).hasSize(1);
  }

  @Test
  void aFunctionPassedAsAValueKeepsItsParametersTainted() {
    assertThat(markup(MAIN + "list.forEach(show)\n", steps())).hasSize(1);
  }
}
