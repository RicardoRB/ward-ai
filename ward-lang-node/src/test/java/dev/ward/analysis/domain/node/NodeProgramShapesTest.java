package dev.ward.analysis.domain.node;

import static dev.ward.analysis.domain.node.NodeProgramDataFixtures.markup;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** The operators around a constant and the shapes of an object literal that is the sink value. */
class NodeProgramShapesTest {
  private static final String HEAD =
      "const Steps = { count: 1, hints: [{ text: 'one' }] }\n"
          + "function show(hint) { el.innerHTML = hint.text }\n"
          + "for (const hint of Steps.hints) { show(hint) }\n";

  private static int reports(final String tail) {
    return markup(HEAD + tail, Map.of()).size();
  }

  @Test
  void anOperatorThatOnlyReadsTheConstantKeepsItData() {
    assertThat(reports("const kind = typeof Steps\n")).isZero();
    assertThat(reports("const negated = !Steps.hints\n")).isZero();
    assertThat(reports("async function wait() { const held = await Steps; return held.count }\n"))
        .isZero();
  }

  @Test
  void anOperatorThatChangesTheConstantKeepsTheFinding() {
    assertThat(reports("delete Steps.hints[0].text\n")).isEqualTo(1);
    assertThat(reports("Steps.count++\n")).isEqualTo(1);
    assertThat(reports("--Steps.count\n")).isEqualTo(1);
    assertThat(reports("Steps.count += req.n\n")).isEqualTo(1);
  }

  @Test
  void anObjectOfConstantsIsData() {
    assertThat(markup("const o = { a: 'x', b: `y`, c: 1 }; el.innerHTML = o", Map.of())).isEmpty();
    assertThat(markup("const o = { f() { return 1 } }; el.innerHTML = o", Map.of())).isEmpty();
  }

  @Test
  void anObjectWithAComputedKeyASpreadOrAGetterIsReported() {
    assertThat(markup("const o = { [k]: 'x' }; el.innerHTML = o", Map.of())).hasSize(1);
    assertThat(markup("const o = { ...other }; el.innerHTML = o", Map.of())).hasSize(1);
    assertThat(markup("const o = { get a() { return req.q } }; el.innerHTML = o", Map.of()))
        .hasSize(1);
    assertThat(markup("const o = { a: req.q }; el.innerHTML = o", Map.of())).hasSize(1);
  }
}
