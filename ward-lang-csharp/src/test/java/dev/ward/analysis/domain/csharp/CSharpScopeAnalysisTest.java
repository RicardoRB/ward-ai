package dev.ward.analysis.domain.csharp;

import static dev.ward.analysis.domain.csharp.CSharpScopeFixtures.read;
import static dev.ward.analysis.domain.csharp.CSharpScopeFixtures.scopes;
import static dev.ward.analysis.domain.csharp.CSharpScopeFixtures.target;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CSharpScopeAnalysisTest {
  private static final int LINE_2 = 2;
  private static final int LINE_3 = 3;
  private static final int FIELD_INITIALIZER = 8;
  private static final int SHADOWED_USE = 12;
  private static final int NESTED_BLOCK = 15;
  private static final int LOOP_BODY = 19;
  private static final int LAMBDA_BODY = 21;
  private static final int RETURN = 22;
  private static final int PRICE = 27;

  private static final String SOURCE =
      """
      using System;
      using Decimal = System.Decimal;

      namespace Shop;

      public class Cart(int limit)
      {
          private int count = limit;

          public int Add(int count)
          {
              var total = count + this.count;
              {
                  var inner = total;
                  total += inner + limit;
              }
              foreach (var item in new[] { 1, 2 })
              {
                  total += item;
              }
              Func<int, int> twice = total => total * 2;
              return Check(total, out var checkedTotal) ? checkedTotal : twice(total);
          }

          private bool Check(int value, out int result) { result = value; return value > 0; }

          public decimal Price(object o) => o is int n ? n : Decimal.Parse("1");
      }
      """;

  private static ScopeKind kindOf(final UnitScopes scopes, final Binding binding) {
    return scopes.scopes().get(binding.scope()).kind();
  }

  @DisplayName("scopes and references")
  @Test
  void everyReferenceResolvesToTheBindingTheCompilerWouldChoose() {
    final UnitScopes scopes = scopes(SOURCE);

    // The parameter shadows the field; this.count is a member access, not a reference.
    assertThat(target(scopes, "count", SHADOWED_USE).kind()).isEqualTo(BindingKind.PARAMETER);
    // A primary constructor parameter is visible in the whole type body.
    final Binding limit = target(scopes, "limit", NESTED_BLOCK);
    assertThat(limit.kind()).isEqualTo(BindingKind.PARAMETER);
    assertThat(kindOf(scopes, limit)).isEqualTo(ScopeKind.CLASS);
    assertThat(target(scopes, "limit", FIELD_INITIALIZER)).isEqualTo(limit);
    // A local of an outer block is visible in a nested block.
    assertThat(target(scopes, "inner", NESTED_BLOCK).kind()).isEqualTo(BindingKind.VARIABLE);
    assertThat(kindOf(scopes, target(scopes, "total", NESTED_BLOCK))).isEqualTo(ScopeKind.BLOCK);
    // The foreach variable lives in the loop.
    assertThat(target(scopes, "item", LOOP_BODY).kind()).isEqualTo(BindingKind.VARIABLE);
    // A lambda parameter shadows the local of the same name inside the lambda only.
    assertThat(kindOf(scopes, target(scopes, "total", LAMBDA_BODY))).isEqualTo(ScopeKind.LAMBDA);
    // An out variable belongs to the statement scope that declares it.
    assertThat(target(scopes, "checkedTotal", RETURN).kind()).isEqualTo(BindingKind.VARIABLE);
    assertThat(target(scopes, "Check", RETURN).kind()).isEqualTo(BindingKind.FUNCTION);
    assertThat(target(scopes, "twice", RETURN).kind()).isEqualTo(BindingKind.VARIABLE);
    // An imported alias is a binding of the file.
    assertThat(target(scopes, "Decimal", PRICE).kind()).isEqualTo(BindingKind.IMPORT);
    // A pattern variable of an expression-bodied member.
    assertThat(target(scopes, "n", PRICE).kind()).isEqualTo(BindingKind.VARIABLE);
    assertThat(scopes.unresolved()).isEmpty();
  }

  @Test
  void aLocalUsedBeforeItsDeclarationStillResolvesToTheLocal() {
    final UnitScopes scopes =
        scopes("class C { int x; void M() {\n  var a = x;\n  int x = 1;\n} }\n");

    final NameReference use = read(scopes, "x", LINE_2);
    assertThat(use.beforeDeclaration()).isTrue();
    assertThat(kindOf(scopes, use.resolved().orElseThrow())).isEqualTo(ScopeKind.BLOCK);
  }

  @Test
  void aTypeWithABaseTypeIsOpenForNamesTheFileDoesNotSpell() {
    final UnitScopes scopes = scopes("class C : Base { void M() { inherited(); } }\n");

    assertThat(scopes.isOpen(read(scopes, "inherited", 1))).isTrue();
  }

  @Test
  void accessorsSeeValueAndField() {
    final UnitScopes scopes =
        scopes("class C { string p { get => field ?? \"\"; set => field = value.Trim(); } }\n");

    assertThat(scopes.unresolved()).isEmpty();
  }

  @Test
  void aQueryRangeVariableIsLocalToTheQuery() {
    final UnitScopes scopes =
        scopes(
            "class C { void M(int[] xs) {\n  var q = from x in xs where x > 1 select x;\n  x;\n} }\n"
        );

    assertThat(scopes.unresolved()).extracting(NameReference::name).containsExactly("x");
    assertThat(target(scopes, "xs", LINE_2).kind()).isEqualTo(BindingKind.PARAMETER);
  }

  @Test
  void aCatchVariableAndAMethodTypeParameterResolve() {
    final UnitScopes scopes =
        scopes(
            "class C { void M<T>(T item) {\n  try { } catch (System.Exception ex) { ex.ToString(); }\n  item.ToString();\n  ex;\n} }\n"
        );

    assertThat(target(scopes, "ex", LINE_2).kind()).isEqualTo(BindingKind.PARAMETER);
    assertThat(scopes.unresolved()).extracting(NameReference::name).containsExactly("ex");
  }

  @Test
  void topLevelStatementsDeclareInTheModuleScope() {
    final UnitScopes scopes = scopes("var n = 1;\nvoid F() { n++; }\nF();\n");

    assertThat(kindOf(scopes, target(scopes, "n", LINE_2))).isEqualTo(ScopeKind.MODULE);
    assertThat(target(scopes, "F", LINE_3).kind()).isEqualTo(BindingKind.FUNCTION);
  }
}
