package dev.ward.lang.swift;

import static dev.ward.lang.swift.SwiftFlowFixtures.count;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Bodies, closures, typed receivers and call sites of the Swift lowering. */
final class SwiftBodyFlowTest {
  @Test
  void aClosureIsPartOfItsStatement() {
    assertThat(count("let ys = [a].map { _ in src() }\nsink(ys)")).isEqualTo(1);
  }

  @Test
  void aNestedFunctionIsABodyOfItsOwn() {
    final String source = "struct C {\nfunc f(a: String) {\nfunc inner() { sink(src()) }\ninner()\n}\n}\n";

    assertThat(SwiftFlowFixtures.names(source)).containsExactlyInAnyOrder("C#f(1)", "C#inner(0)");
    assertThat(SwiftFlowFixtures.file(source)).hasSize(1);
  }

  @Test
  void bodiesAreNamedByOwnerNameAndArity() {
    final String source =
        "struct A {\nstruct B {\nfunc h() {}\n}\ninit(x: Int, y: Int) {}\n}\nextension Foo {\nfunc g(_ a: Int) {}\n}\nfunc top() {}\nprotocol P {\nfunc req()\n}\n";

    assertThat(SwiftFlowFixtures.names(source))
        .containsExactly("A.B#h(0)", "A#init(2)", "Foo#g(1)", "top(0)");
  }

  @Test
  void aWriteToTheMemberOfATypedLocalIsASink() {
    assertThat(count("let p = Process()\np.arguments = [src()]")).isEqualTo(1);
    assertThat(count("let q = Other()\nq.arguments = [src()]")).isZero();
    assertThat(count("var r: Process? = nil\nr?.arguments = [src()]")).isEqualTo(1);
  }

  @Test
  void aParameterWithADeclaredTypeIsTyped() {
    final String source = "struct C {\nfunc f(a: Process) {\na.arguments = [src()]\n}\n}\n";

    assertThat(SwiftFlowFixtures.file(source)).hasSize(1);
  }

  @Test
  void aValueFromAMemberReadSource() {
    assertThat(count("let x = a.tainted\nsink(x)")).isEqualTo(1);
  }

  @Test
  void aCompoundAssignmentKeepsTheTaintOfTheTarget() {
    assertThat(count("var x = src()\nx += \"!\"\nsink(x)")).isEqualTo(1);
  }

  @Test
  void aSubscriptAndATrailingClosureAreWalked() {
    assertThat(count("let xs = [src()]\nlet y = xs[0]\nsink(y)")).isEqualTo(1);
    assertThat(count("var z = \"\"\nrun { z = src() }\nsink(z)")).isEqualTo(1);
  }

  @Test
  void aCreationIsNamedWithItsFirstLabel() {
    final List<FlowSite> sites =
        SwiftFlowFixtures.graph(
            "struct C {\nfunc f(a: String) {\nlet s = String(contentsOfFile: a)\nlet t = String(a)\n}\n}\n",
            "C#f(1)"
        ).blocks().stream().flatMap(block -> block.sites().stream()).toList();

    assertThat(sites)
        .filteredOn(site -> site.kind() == FlowSiteKind.CALL)
        .extracting(FlowSite::name)
        .containsExactly("String(contentsOfFile:)", "String");
  }

  @Test
  void aSharedInstanceAndACreationNameTheirType() {
    final List<FlowSite> sites =
        SwiftFlowFixtures.graph(
            "struct C {\nfunc f(a: String) {\nFileManager.default.contents(atPath: a)\nURLComponents(string: a)?.queryItems\n}\n}\n",
            "C#f(1)"
        ).blocks().stream().flatMap(block -> block.sites().stream()).toList();

    assertThat(sites)
        .filteredOn(site -> "contents".equals(site.name()) || "queryItems".equals(site.name()))
        .extracting(site -> site.receiverType().orElse(""))
        .containsExactly("FileManager", "URLComponents");
  }
}
