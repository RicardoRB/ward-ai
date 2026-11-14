package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A {@code val} that holds a new object has that object's type, and calls on it name the object.
 */
class KotlinConstructedReceiverTest {

  private static final String IMPORT = "import javax.naming.InitialContext\n";

  private static FlowSite lookup(final String body) {
    final String source = IMPORT + "class C {\n  fun f(a: String) {\n" + body + "\n  }\n}\n";
    final List<FlowSite> sites =
        KotlinFlowFixtures.graph(source, "C#f(1)").blocks().stream()
            .map(BasicBlock::sites)
            .flatMap(List::stream)
            .filter(site -> site.kind() == FlowSiteKind.CALL && "lookup".equals(site.name()))
            .toList();
    assertThat(sites).hasSize(1);
    return sites.get(0);
  }

  @Test
  void aValThatHoldsANewObjectGivesItsTypeAndNamesTheObjectAsTheReceiver() {
    final FlowSite site = lookup("val c = InitialContext()\nc.lookup(a)");

    assertThat(site.receiverType()).contains("javax.naming.InitialContext");
    assertThat(site.qualifier()).isEqualTo("InitialContext()");
  }

  @Test
  void aVarKeepsItsNameAndHasNoType() {
    final FlowSite site = lookup("var c = InitialContext()\nc.lookup(a)");

    assertThat(site.receiverType()).isEqualTo(Optional.empty());
    assertThat(site.qualifier()).isEqualTo("c");
  }

  @Test
  void aDeclaredTypeIsKeptAsItIs() {
    final FlowSite site = lookup("val c: InitialContext = InitialContext()\nc.lookup(a)");

    assertThat(site.receiverType()).contains("javax.naming.InitialContext");
    assertThat(site.qualifier()).isEqualTo("c");
  }

  @Test
  void aValueThatIsMoreThanTheConstructorCallIsNotTheObject() {
    final FlowSite site = lookup("val c = InitialContext().also { }\nc.lookup(a)");

    assertThat(site.receiverType()).isEqualTo(Optional.empty());
    assertThat(site.qualifier()).isEqualTo("c");
  }

  @Test
  void aCallOfALowerCaseFunctionIsNotAConstructor() {
    final FlowSite site = lookup("val c = make()\nc.lookup(a)");

    assertThat(site.receiverType()).isEqualTo(Optional.empty());
    assertThat(site.qualifier()).isEqualTo("c");
  }
}
