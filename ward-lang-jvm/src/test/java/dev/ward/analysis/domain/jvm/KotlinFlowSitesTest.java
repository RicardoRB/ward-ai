package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.KotlinFlowFixtures.sites;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The flow sites that the Kotlin lowering records for the taint rules. */
class KotlinFlowSitesTest {

  private static final String IMPORTS =
      "import javax.servlet.http.HttpServletRequest\n"
          + "import java.sql.Statement\n"
          + "import java.io.File\n"
          + "import org.springframework.web.bind.annotation.RequestParam\n";

  private static List<FlowSite> calls(final String function, final int parameters) {
    return sites(function, parameters).stream()
        .filter(site -> site.kind() == FlowSiteKind.CALL)
        .toList();
  }

  @Test
  void aTypedParameterGivesItsCallsAReceiverType() {
    final List<FlowSite> found = calls("fun f(st: Statement, q: String) { st.executeQuery(q) }", 2);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).name()).isEqualTo("executeQuery");
    assertThat(found.get(0).qualifier()).isEqualTo("st");
    assertThat(found.get(0).receiverType()).contains("Statement");
  }

  @Test
  void importsResolveTheDeclaredType() {
    final String source =
        "package p\n"
            + IMPORTS
            + "\nclass C { fun f(st: Statement?, q: String) { st?.executeQuery(q) }\n}\n";
    final FlowSite call =
        KotlinFlowFixtures.graph(source, "C#f(2)").blocks().stream()
            .flatMap(block -> block.sites().stream())
            .filter(site -> site.kind() == FlowSiteKind.CALL)
            .findFirst()
            .orElseThrow();

    assertThat(call.receiverType()).contains("java.sql.Statement");
    assertThat(call.arguments()).hasSize(1);
  }

  @Test
  void aSafeCallChainListsInnerCallsFirstAndKeepsTheQualifierText() {
    final List<FlowSite> found =
        calls(
            "fun f(r: HttpServletRequest) { val v = r?.getParameter(\"a\")?.trim() ?: \"d\" }", 1);

    assertThat(found).extracting(FlowSite::name).containsExactly("getParameter", "trim");
    assertThat(found.get(0).qualifier()).isEqualTo("r");
    assertThat(found.get(1).qualifier()).isEqualTo("r.getParameter(\"a\")");
  }

  @Test
  void aCallInsideAnArgumentComesBeforeTheCallThatTakesIt() {
    final List<FlowSite> found = calls("fun f(a: String) { outer(inner(a), 2) }", 1);

    assertThat(found).extracting(FlowSite::name).containsExactly("inner", "outer");
    assertThat(found.get(1).arguments()).hasSize(2);
  }

  @Test
  void namedAndDefaultArgumentsKeepTheirValueSpans() {
    final String source =
        "class C {\n fun g(a: Int, b: String = \"x\") {}\n fun f() { g(b = \"y\", a = 1) }\n}\n";
    final FlowSite call =
        KotlinFlowFixtures.graph(source, "C#f(0)").blocks().stream()
            .flatMap(block -> block.sites().stream())
            .findFirst()
            .orElseThrow();

    assertThat(call.arguments()).hasSize(2);
    assertThat(
            source.substring(
                call.arguments().get(0).startOffset(), call.arguments().get(0).endOffset()))
        .isEqualTo("\"y\"");
  }

  @Test
  void aTrailingLambdaIsNotAnArgumentButItsCallsAreSites() {
    final List<FlowSite> found = calls("fun f(a: String) { run(a) { println(it) } }", 1);

    assertThat(found).extracting(FlowSite::name).containsExactly("run", "println");
    assertThat(found.get(0).arguments()).hasSize(1);
  }
}
