package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The illegal-type check on declarations: fields, locals, parameters and return types. */
class IllegalTypeCheckTest extends TreeBuilders {

  private static final String MESSAGE = "illegal {value}";
  private static final Set<String> ILLEGAL = Set.of("HashMap", "java.util.TreeSet");

  @Test
  void fieldLocalParameterAndReturnTypesNamedAsIllegalAreReported() {
    final SyntaxNode field = n("fieldDeclaration", typeType("HashMap<String,String>"));
    assertThat(findings(unit(field))).hasSize(1);
    final SyntaxNode local = n("localVariableDeclaration", typeType("HashMap"));
    assertThat(findings(unit(local))).hasSize(1);
    final SyntaxNode parameter = n("formalParameter", typeType("java.util.TreeSet<Integer>"));
    assertThat(findings(unit(parameter))).hasSize(1);
    final SyntaxNode method =
        n("methodDeclaration", n("typeTypeOrVoid", t(TOKEN_IDENTIFIER, "HashMap")));
    assertThat(findings(unit(method))).hasSize(1);
  }

  @Test
  void interfaceTypesAndArraysOfOtherNamesAreNotReported() {
    assertThat(findings(unit(n("localVariableDeclaration", typeType("Map<String,String>")))))
        .isEmpty();
    assertThat(findings(unit(n("localVariableDeclaration", typeType("Object[]"))))).isEmpty();
    assertThat(findings(unit(n("localVariableDeclaration", typeType("HashMapX"))))).isEmpty();
  }

  private static java.util.List<dev.ward.analysis.domain.rule.RuleMatch> findings(
      final SyntaxNode root) {
    return new IllegalTypeCheck(ILLEGAL, MESSAGE).check(root, FILE);
  }

  private static SyntaxNode typeType(final String text) {
    return n("typeType", t(TOKEN_IDENTIFIER, text));
  }
}
