package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A parameter typed Request that destructures request members gets one parameter site each. */
class NodeRequestParameterSitesTest {

  @Test
  void destructuredRequestMembersAreParameterSites() {
    assertThat(names("function run({ params, query }: Request, res) {}"))
        .containsExactly("params", "query");
  }

  @Test
  void expressRequestTypeIsRecognizedToo() {
    assertThat(names("function run({ body }: express.Request) {}")).containsExactly("body");
  }

  @Test
  void otherMembersAndOtherTypesAreNotSources() {
    assertThat(names("function run({ method }: Request) {}")).isEmpty();
    assertThat(names("function run({ params }: Other) {}")).isEmpty();
    assertThat(names("function run(req: Request) {}")).isEmpty();
  }

  private static List<String> names(final String source) {
    return NodeTaintFixtures.graph(AnalysisLanguage.TYPESCRIPT, source, "run").blocks().stream()
        .map(BasicBlock::sites)
        .flatMap(List::stream)
        .filter(site -> site.kind() == FlowSiteKind.PARAMETER)
        .filter(site -> site.annotations().contains(NodeParameterSites.EXPRESS_REQUEST))
        .map(FlowSite::name)
        .toList();
  }
}
