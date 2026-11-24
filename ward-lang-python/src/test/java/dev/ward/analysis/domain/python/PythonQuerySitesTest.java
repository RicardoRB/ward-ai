package dev.ward.analysis.domain.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The sites for the value parts of a string that follow a literal question mark. */
class PythonQuerySitesTest {
  private static List<FlowSite> queries(final String expression) {
    return PythonFlowSitesTest.sites("def f(a, b):\n    x = " + expression + "\n", "f").stream()
        .filter(site -> site.kind() == FlowSiteKind.QUERY_PART)
        .toList();
  }

  @Test
  void operandsAfterTheLiteralQuestionMarkAreQueryParts() {
    final List<FlowSite> found = queries("'/p?' + a + '&' + b");
    assertThat(found).hasSize(1);
    assertThat(found.get(0).arguments()).hasSize(2);
  }

  @Test
  void anOperandBeforeTheQuestionMarkIsNotAQueryPart() {
    final List<FlowSite> found = queries("a + '?x=' + b");
    assertThat(found).hasSize(1);
    assertThat(found.get(0).arguments()).hasSize(1);
  }

  @Test
  void aConcatenationWithoutQuestionMarkHasNone() {
    assertThat(queries("'/p/' + a")).isEmpty();
  }

  @Test
  void fieldsAfterTheQuestionMarkOfAnFStringAreQueryParts() {
    final List<FlowSite> found = queries("f'/p?q={a}&r={b}'");
    assertThat(found).hasSize(1);
    assertThat(found.get(0).arguments()).hasSize(2);
  }

  @Test
  void aFieldBeforeTheQuestionMarkOfAnFStringIsNotOne() {
    final List<FlowSite> found = queries("f'{a}?q={b}'");
    assertThat(found.get(0).arguments()).hasSize(1);
  }

  @Test
  void aSubtractionIsNotFollowed() {
    assertThat(queries("'?' - a")).isEmpty();
  }
}
