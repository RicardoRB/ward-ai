package dev.ward.analysis.domain.rust;

import static dev.ward.analysis.domain.rust.RustScopeFixtures.assertResolves;
import static dev.ward.analysis.domain.rust.RustScopeFixtures.scopes;
import static dev.ward.analysis.domain.rust.RustScopeFixtures.uses;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import org.junit.jupiter.api.Test;

class RustScopeNamesTest {
  @Test
  void aStructLiteralShorthandReadsTheVariableAndAFieldNameIsNotAReference() {
    final UnitScopes scopes =
        scopes("""
            struct P { x: u8, y: u8 }
            fn f(x: u8, y: u8) -> P {
                P { x, y: y + 1 }
            }
            """);

    assertResolves(scopes, "x@3=2", "y@3=2", "P@3=1");
    assertThat(uses(scopes, "x")).isEqualTo(1);
  }

  @Test
  void aMemberNameIsNotAReferenceButItsReceiverIs() {
    final UnitScopes scopes =
        scopes("""
            fn f(v: Vec<u8>, len: u8) -> usize {
                v.len() + v.iter().count() + (len as usize)
            }
            """);

    assertThat(uses(scopes, "len")).isEqualTo(1);
    assertResolves(scopes, "len@2=1", "v@2=1");
  }

  @Test
  void anAssignmentWritesAndACompoundAssignmentReadsAndWrites() {
    final UnitScopes scopes =
        scopes("""
            fn f() {
                let mut n = 0;
                n = 1;
                n += 2;
                self::g(n);
            }
            """);

    final List<ReferenceAccess> accesses =
        scopes.references().stream()
            .filter(use -> "n".equals(use.name()) && use.span().startLine() > 2)
            .map(NameReference::access)
            .toList();

    assertThat(accesses)
        .containsExactly(
            ReferenceAccess.WRITE,
            ReferenceAccess.READ,
            ReferenceAccess.WRITE,
            ReferenceAccess.READ
        );
  }

  @Test
  void aMacroReadsItsIdentifiersAndTheNamesOfItsFormatString() {
    final UnitScopes scopes =
        scopes("""
            fn f(a: u8, b: u8, width: usize) {
                println!("{a:>width$} {{literal}} {} \\
                    {b}", a.max(1), obj.field);
                assert_eq!(a, b);
                let v = vec![a; 3];
            }
            """);

    assertResolves(scopes, "a@2=1", "width@2=1", "b@3=1", "a@4=1", "a@5=1");
    assertThat(uses(scopes, "literal")).isZero();
    assertThat(uses(scopes, "field")).isZero();
  }
}
