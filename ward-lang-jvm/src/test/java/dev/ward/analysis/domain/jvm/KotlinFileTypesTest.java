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
}
