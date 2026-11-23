package dev.ward.analysis.domain.rust;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class RustGetFirstCheckTest {
  private static final int SIX_CALLS = 6;
  private static final int CALL_LINE = 8;
  private static final int IMPL_H_LINE = 5;
  private static final int IMPL_G_LINE = 6;
  private static final String OWN_GET =
      "pub struct G(Vec<String>);\n"
          + "impl G { pub fn get(&self, i: usize) -> u8 { 0 } }\n"
          + "pub trait Lookup { fn get(&self, i: usize) -> u8; }\n";

  private static List<Integer> lines(final String source) {
    final List<RuleMatch> found =
        new RustGetFirstCheck("m").matches(RustScopeFixtures.tree(source));
    return found.stream().map(match -> match.span().startLine()).toList();
  }

  @Test
  void aSliceVecArrayAndVecDequeAreReportedEvenWhereTheFileDefinesGet() {
    final String source = OWN_GET
        + "fn f(s: &[u8], v: Vec<u8>, q: &mut VecDeque<u8>) {\n"
        + "  let a = [1, 2];\n  let b = vec![1];\n  let c = Vec::new();\n"
        + "  s.get(0); v.get(0); q.get(0); a.get(0); b.get(0); c.get(0);\n}\n";

    assertThat(lines(source)).hasSize(SIX_CALLS).containsOnly(CALL_LINE);
  }

  @Test
  void aReceiverThatDefinesGetOrIsAMapIsSkipped() {
    final String source = OWN_GET
        + "fn f(g: &G, l: &Lookup, m: HashMap<u8, u8>) {\n"
        + "  let n = G::new();\n  g.get(0); l.get(0); m.get(0); n.get(0);\n}\n";

    assertThat(lines(source)).isEmpty();
  }

  @Test
  void selfAndItsFieldsAreTypedFromTheImplAndTheStruct() {
    final String source = OWN_GET
        + "pub struct H { items: Vec<u8>, inner: G }\n"
        + "impl H { fn a(&self) { self.items.get(0); self.inner.get(0); self.get(0); } }\n"
        + "impl G { fn b(&self) { self.get(0); self.0.get(0); } }\n";

    assertThat(lines(source)).containsExactly(IMPL_H_LINE, IMPL_H_LINE, IMPL_G_LINE);
  }

  @Test
  void anUnknownReceiverIsReportedOnlyInAFileWithoutGet() {
    assertThat(lines("fn f(x: u8) { y.get(0); }")).hasSize(1);
    assertThat(lines(OWN_GET + "fn f(x: u8) { y.get(0); }")).isEmpty();
  }

  @Test
  void aNameBoundTwiceWithDifferentTypesIsUnknown() {
    final String source = "fn f() { let a = vec![1]; let a = G::new(); a.get(0); }\n";

    assertThat(lines(source)).hasSize(1);
    assertThat(lines(OWN_GET + source)).isEmpty();
  }
}
