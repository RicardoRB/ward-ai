package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The types that a file declares for properties and functions reach the calls of its methods. */
class KotlinFileTypesTest {
  private static final String IMPORT = "import javax.naming.InitialContext\n";

  private static FlowSite lookupIn(final String members, final String body) {
    final String source =
        IMPORT + "class C {\n" + members + "\n  fun f(a: String) {\n" + body + "\n  }\n}\n";
    return KotlinFlowFixtures.graph(source, "C#f(1)").blocks().stream()
        .map(BasicBlock::sites)
        .flatMap(List::stream)
        .filter(site -> site.kind() == FlowSiteKind.CALL && "lookup".equals(site.name()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aPropertyOfTheClassGivesItsDeclaredTypeToACallInAMethod() {
    final FlowSite site = lookupIn("private val c: InitialContext = make()", "c.lookup(a)");

    assertThat(site.receiverType()).contains("javax.naming.InitialContext");
  }

  @Test
  void aPropertyThatHoldsANewObjectNamesTheObjectInAMethod() {
    final FlowSite site = lookupIn("private val c = InitialContext()", "c.lookup(a)");

    assertThat(site.receiverType()).contains("javax.naming.InitialContext");
    assertThat(site.qualifier()).isEqualTo("InitialContext()");
  }

  @Test
  void aLocalFromAFunctionOfTheFileHasItsDeclaredReturnType() {
    final FlowSite site =
        lookupIn("fun make(): InitialContext = InitialContext()", "val c = make()\nc.lookup(a)");

    assertThat(site.receiverType()).contains("javax.naming.InitialContext");
  }

  @Test
  void aNameDeclaredWithTwoTypesInTheFileHasNoType() {
    final FlowSite site =
        lookupIn("val c: InitialContext = make()\nval other = 1", "c.lookup(a)");
    final FlowSite clash =
        lookupIn("val c: InitialContext = make()\nfun g(c: String) {}", "c.lookup(a)");

    assertThat(site.receiverType()).contains("javax.naming.InitialContext");
    assertThat(clash.receiverType()).isEqualTo(Optional.empty());
  }

  private static FlowSite callIn(final String source, final String name) {
    return KotlinFlowFixtures.graph(source, "C#f(1)").blocks().stream()
        .map(BasicBlock::sites)
        .flatMap(List::stream)
        .filter(site -> site.kind() == FlowSiteKind.CALL && name.equals(site.name()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aConstructorPropertyGivesItsDeclaredTypeToACallInAMethod() {
    final FlowSite site =
        callIn(
            IMPORT + "class C(private val c: InitialContext) {\n fun f(a: String) { c.lookup(a) }\n}\n",
            "lookup"
        );

    assertThat(site.receiverType()).contains("javax.naming.InitialContext");
  }

  @Test
  void aLoggerFromAFactoryHasTheTypeOfTheLogger() {
    final String factories =
        "import org.slf4j.LoggerFactory\nimport java.util.logging.Logger\n"
            + "class C {\n private val log = LoggerFactory.getLogger(C::class.java)\n"
            + " fun f(a: String) {\n log.info(a)\n val l = Logger.getLogger(\"x\")\n l.warning(a)\n }\n}\n";

    assertThat(callIn(factories, "info").receiverType()).contains("org.slf4j.Logger");
    assertThat(callIn(factories, "warning").receiverType())
        .contains("java.util.logging.Logger");
  }

  @Test
  void aCallOfAnUnknownFactoryHasNoType() {
    final FlowSite site =
        callIn(
            "class C {\n private val log = Other.make()\n fun f(a: String) { log.info(a) }\n}\n",
            "info"
        );

    assertThat(site.receiverType()).isEqualTo(Optional.empty());
  }

  @Test
  void anAnnotationFromAWildcardImportNamesEachPackageItMayComeFrom() {
    final String source =
        "import org.springframework.web.bind.annotation.*\nclass C {\n"
            + " fun f(@RequestParam a: String) {\n }\n}\n";
    final List<String> annotations =
        KotlinFlowFixtures.graph(source, "C#f(1)").blocks().stream()
            .flatMap(block -> block.sites().stream())
            .filter(site -> site.kind() == FlowSiteKind.PARAMETER)
            .flatMap(site -> site.annotations().stream())
            .toList();

    assertThat(annotations)
        .contains("org.springframework.web.bind.annotation.RequestParam");
  }
}
