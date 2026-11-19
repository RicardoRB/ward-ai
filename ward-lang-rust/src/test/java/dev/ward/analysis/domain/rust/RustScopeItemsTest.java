package dev.ward.analysis.domain.rust;

import static dev.ward.analysis.domain.rust.RustScopeFixtures.assertResolves;
import static dev.ward.analysis.domain.rust.RustScopeFixtures.named;
import static dev.ward.analysis.domain.rust.RustScopeFixtures.scopes;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.UnitScopes;
import org.junit.jupiter.api.Test;

class RustScopeItemsTest {
  @Test
  void anItemOfABlockIsVisibleBeforeItsDeclaration() {
    final UnitScopes scopes =
        scopes("""
            fn f() -> u8 {
                let a = helper();
                fn helper() -> u8 { 1 }
                a
            }
            """);

    assertResolves(scopes, "helper@2=3");
    assertThat(scopes.references()).noneMatch(NameReference::beforeDeclaration);
  }

  @Test
  void aConstInitializerMayUseALaterConstant() {
    final UnitScopes scopes = scopes("const A: u8 = B + 1;\nconst B: u8 = 2;\n");

    assertResolves(scopes, "B@1=2");
    assertThat(scopes.references()).noneMatch(NameReference::beforeDeclaration);
  }

  @Test
  void aGlobImportMakesItsScopeOpenAndAnExportedItemIsFlagged() {
    final UnitScopes scopes =
        scopes("""
            mod inner {
                use super::*;
                pub fn visible() { hidden(); }
            }
            pub use self::inner::visible as shown;
            use std::fmt::Write as _;
            """);

    assertThat(
        scopes.references().stream()
            .filter(use -> "hidden".equals(use.name()))
            .allMatch(scopes::isOpen)
    )
        .isTrue();
    assertThat(named(scopes, "visible").get(0).has(BindingFlag.EXPORTED)).isTrue();
    assertThat(named(scopes, "shown").get(0).kind()).isEqualTo(BindingKind.IMPORT);
    assertThat(named(scopes, "shown").get(0).has(BindingFlag.EXPORTED)).isTrue();
    assertThat(named(scopes, "_")).isEmpty();
    assertThat(named(scopes, "Write")).isEmpty();
  }

  @Test
  void anImportRecordsWhereItComesFrom() {
    final UnitScopes scopes =
        scopes("use std::collections::{self, HashMap as Map};\nextern crate alloc as heap;\n");

    final Binding map = named(scopes, "Map").get(0);
    final Binding module = named(scopes, "collections").get(0);

    assertThat(map.origin().orElseThrow().specifier()).isEqualTo("std::collections");
    assertThat(map.origin().orElseThrow().member()).isEqualTo("HashMap");
    assertThat(module.origin().orElseThrow().specifier()).isEqualTo("std::collections");
    assertThat(module.origin().orElseThrow().member()).isEmpty();
    assertThat(named(scopes, "heap")).hasSize(1);
  }

  @Test
  void genericParametersAndSelfAreDeclaredInTheItemsScope() {
    final UnitScopes scopes =
        scopes("""
            struct W<T: Clone, const N: usize>([T; N]);
            impl<T: Clone, const N: usize> W<T, N> {
                fn first(&self, other: T) -> Option<T> where T: Copy {
                    let _ = self.0.len();
                    Some(other)
                }
            }
            """);

    assertResolves(scopes, "T@3=2", "N@2=2", "self@4=3", "W@2=1");
    assertThat(named(scopes, "first")).isEmpty();
  }
}
