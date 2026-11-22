package dev.ward.analysis.domain.rust;

import static dev.ward.analysis.domain.rust.RustScopeFixtures.assertResolves;
import static dev.ward.analysis.domain.rust.RustScopeFixtures.named;
import static dev.ward.analysis.domain.rust.RustScopeFixtures.scopes;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.UnitScopes;
import org.junit.jupiter.api.Test;

class RustScopeBindingsTest {
  @Test
  void aLetElseReadsItsValueAndRunsItsElseBlockBeforeTheNamesExist() {
    final UnitScopes scopes =
        scopes("""
            fn f(value: Option<u8>) -> u8 {
                let Some(value) = value else {
                    return value.unwrap_or(0);
                };
                value
            }
            """);

    assertResolves(scopes, "value@2=1", "value@3=1", "value@5=2");
  }

  @Test
  void anIfLetBindsInItsConditionAndBodyButNotInTheElseBranch() {
    final UnitScopes scopes =
        scopes("""
            fn f(x: Option<u8>, y: u8) -> u8 {
                if let Some(y) = x && y > 1 {
                    y
                } else {
                    y
                }
            }
            """);

    assertResolves(scopes, "y@2=2", "y@3=2", "y@5=1");
  }

  @Test
  void aWhileLetAndAForLoopBindOnlyInTheirBody() {
    final UnitScopes scopes =
        scopes("""
            fn f(mut stack: Vec<u8>, item: u8) {
                while let Some(item) = stack.pop() {
                    let _ = item;
                }
                for item in 0..3 {
                    let _ = item;
                }
                let _ = item;
            }
            """);

    assertResolves(scopes, "item@3=2", "item@6=5", "item@8=1");
  }

  @Test
  void anOrPatternBindsEachNameOnceAndAnUpperCaseNameIsAConstantNotABinding() {
    final UnitScopes scopes =
        scopes("""
            const LIMIT: u8 = 3;
            enum E { A(u8, u8), B(u8, u8), None }
            fn f(e: E) -> u8 {
                match e {
                    E::A(x, y) | E::B(y, x) => x + y,
                    LIMIT => 0,
                    None => 1,
                }
            }
            """);

    assertThat(named(scopes, "x")).hasSize(1);
    assertThat(named(scopes, "y")).hasSize(1);
    assertThat(named(scopes, "None")).isEmpty();
    assertResolves(scopes, "LIMIT@6=1");
  }

  @Test
  void aClosureParameterShadowsTheOuterNameAndADestructuredOneIsFlagged() {
    final UnitScopes scopes =
        scopes("""
            fn f(x: u8) {
                let g = move |x: u8, (a, b): (u8, u8)| x + a + b;
                let _ = (g, x);
            }
            """);

    assertResolves(scopes, "x@2=2", "x@3=1");
    assertThat(named(scopes, "a").get(0).has(BindingFlag.DESTRUCTURED)).isTrue();
  }

  @Test
  void aStructPatternBindsItsShorthandFieldsAndItsRenamedOnes() {
    final UnitScopes scopes =
        scopes("""
            struct P { x: u8, y: u8, z: u8 }
            fn f(p: P) -> u8 {
                let P { x, y: why, ref z, .. } = p;
                x + why + *z
            }
            """);

    assertThat(named(scopes, "y")).isEmpty();
    assertResolves(scopes, "x@4=3", "why@4=3", "z@4=3");
  }

  @Test
  void aRawIdentifierIsTheNameWithoutItsPrefix() {
    final UnitScopes scopes = scopes("fn f() { let r#type = 1; let _ = r#type; }");

    assertThat(named(scopes, "type")).hasSize(1);
  }
}
