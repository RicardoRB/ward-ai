package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.KotlinTaintFixtures.count;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Kotlin shows a Java getter as a property; a rule that names the getter still finds it. */
class KotlinPropertySyntaxTest {

  @Test
  void aPropertyReadIsAlsoTheCallOfItsJavaGetter() {
    final List<FlowSite> sites =
        KotlinFlowFixtures.sites("fun f(request: Any) { val m = request.parameterMap }", 1);

    assertThat(sites)
        .filteredOn(site -> site.kind() == FlowSiteKind.CALL)
        .extracting(FlowSite::name, FlowSite::qualifier)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("getParameterMap", "request"));
    assertThat(sites).filteredOn(site -> site.kind() == FlowSiteKind.MEMBER_READ).hasSize(1);
  }

  @Test
  void aBooleanPropertyKeepsItsIsPrefix() {
    final List<FlowSite> sites =
        KotlinFlowFixtures.sites("fun f(request: Any) { val s = request.isSecure }", 1);

    assertThat(sites)
        .filteredOn(site -> site.kind() == FlowSiteKind.CALL)
        .extracting(FlowSite::name)
        .containsExactly("isSecure");
  }

  @Test
  void aSourceNamedByItsGetterIsFoundThroughThePropertySyntax() {
    assertThat(count("val q = a.tainted\nsink(q)")).isOne();
  }

  @Test
  void aJavaGetterCalledByNameIsStillASource() {
    assertThat(count("val q = a.getTainted()\nsink(q)")).isOne();
  }

  @Test
  void aReadThatNoSpecNamesIsNotASource() {
    assertThat(count("val q = a.length\nsink(q)")).isZero();
  }
}
