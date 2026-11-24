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
  private static final int IMPL_USE_LINE = 4;
  private static final int FREE_LINE = 7;
  private static final int ALIAS_LINE = 3;
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

    assertThat(lines(source)).containsExactly(IMPL_H_LINE, IMPL_G_LINE);
  }

  @Test
  void aReceiverOfUnknownTypeIsNeverReported() {
    assertThat(lines("fn f(x: u8) { y.get(0); }")).isEmpty();
    assertThat(lines(OWN_GET + "fn f(x: u8) { y.get(0); }")).isEmpty();
    assertThat(lines("fn f() { let a = vec![1]; let a = G::new(); a.get(0); }\n")).isEmpty();
  }

  @Test
  void aLocalIsTypedByTheReturnTypeOfTheFunctionItComesFrom() {
    final String source = "struct M; impl M {\n"
        + "  fn rows(&self) -> Result<Vec<u8>, E> { todo!() }\n"
        + "  fn caps(&self) -> Result<Self::Captures, E> { todo!() }\n"
        + "  fn use_it(&self) { let a = self.rows()?; a.get(0); let b = self.caps()?; b.get(0); }\n"
        + "}\n"
        + "fn all() -> Option<&[u8]> { todo!() }\n"
        + "fn g(m: M) { let c = all().unwrap(); c.get(0); let d = m.rows().expect(\"x\"); d.get(0); }\n"
        + "trait T { fn caps(&self) -> Result<Self::Captures, E>; }\n"
        + "fn h(m: M) { let e = m.caps()?; e.get(0); }\n";

    assertThat(lines(source)).containsExactly(IMPL_USE_LINE, FREE_LINE, FREE_LINE);
  }

  @Test
  void aTypeThatAnImplFixesIsFollowedAndOneDeclaredTwiceIsNot() {
    final String source = "struct M; impl T for M { type Captures = Vec<u8>;\n"
        + "  fn caps(&self) -> Self::Captures { todo!() }\n"
        + "  fn f(&self) { let a = self.caps(); a.get(0); } }\n"
        + "fn dup() -> Vec<u8> { todo!() } fn dup() -> Foo { todo!() }\n"
        + "fn g() { let b = dup(); b.get(0); }\n";

    assertThat(lines(source)).containsExactly(ALIAS_LINE);
  }

  @Test
  void forAndClosureBindingsTakeTheItemTypeOfAKnownCollection() {
    final String source = "fn f(rows: Vec<Vec<u8>>, names: Vec<G>, flat: &[u8]) {\n"
        + "  for row in &rows { row.get(0); }\n"
        + "  for n in names.iter() { n.get(0); }\n"
        + "  for b in flat { b.get(0); }\n"
        + "  rows.iter().map(|r| r.get(0));\n}\n";

    assertThat(lines(OWN_GET + source)).containsExactly(IMPL_H_LINE, CALL_LINE);
  }

  @Test
  void aMutableSliceParameterIsTyped() {
    final String source = "fn r(mut bytes: &mut [u8], src: u8) -> Option<usize> {\n"
        + "  let p = bytes.find_byte(src)?;\n"
        + "  bytes = &mut bytes[p + 1..];\n"
        + "  while bytes.get(0) == Some(&src) { }\n  None\n}\n";

    assertThat(lines(source)).hasSize(1);
  }

  @Test
  void aNewtypeThatDerefsToAVecIsASequence() {
    final String source = "struct T(Vec<u8>);\n"
        + "impl Deref for T { type Target = Vec<u8>; fn deref(&self) -> &Vec<u8> { &self.0 } }\n"
        + "struct U { t: T }\n"
        + "impl U { fn f(&self) { self.t.get(0); } }\n";

    assertThat(lines(source)).containsExactly(IMPL_USE_LINE);
  }
}
