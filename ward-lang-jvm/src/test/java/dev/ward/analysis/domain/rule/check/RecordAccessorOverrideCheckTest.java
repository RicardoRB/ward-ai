package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.unitMatches;
import static dev.ward.analysis.domain.rule.check.TreeBuilders.n;
import static dev.ward.analysis.domain.rule.check.TreeBuilders.t;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordAccessorOverrideCheckTest {

  private static final String MESSAGE = "message";
  private static final String IDENTIFIER = "identifier";
  private static final String NO_PARAMETERS = "()";
  private static final String NAME = "x";

  @Test
  void reportsAnAccessorWithoutOverrideAndAcceptsOneWithIt() {
    assertThat(run(record(classMember(method(NAME, NO_PARAMETERS), false)))).hasSize(1);
    assertThat(run(record(classMember(method(NAME, NO_PARAMETERS), true)))).isEmpty();
    assertThat(run(record(classMember(bareMethod(NAME), false)))).hasSize(1);
  }

  @Test
  void ignoresMethodsWithParametersOrOtherNames() {
    assertThat(run(record(classMember(method(NAME, "(int a)"), false)))).isEmpty();
    assertThat(run(record(classMember(method("other", NO_PARAMETERS), false)))).isEmpty();
  }

  @Test
  void ignoresMembersThatAreNotMethodsOfARecord() {
    final SyntaxNode field =
        n("classBodyDeclaration", n("memberDeclaration", n("fieldDeclaration")));
    assertThat(run(record(field))).isEmpty();
    assertThat(run(record(n("otherMember", n("memberDeclaration"))))).isEmpty();
    final SyntaxNode classDeclaration =
        n("classDeclaration", n("classBody", classMember(method(NAME, NO_PARAMETERS), false)));
    assertThat(run(classDeclaration)).isEmpty();
  }

  @Test
  void readsComponentNamesFromTheLastIdentifierOfEachComponent() {
    final SyntaxNode method = classMember(method(NAME, NO_PARAMETERS), false);
    final SyntaxNode header =
        n(
            "recordHeader",
            n(
                "formalParameterList",
                n("recordComponent", t(IDENTIFIER, "int"), t(IDENTIFIER, NAME))));
    assertThat(run(recordWithHeader(header, method))).hasSize(1);
    final SyntaxNode empty = n("recordHeader", n("formalParameterList", n("recordComponent")));
    assertThat(run(recordWithHeader(empty, method))).isEmpty();
  }

  private static List<RuleMatch> run(final SyntaxNode declaration) {
    return unitMatches(new RecordAccessorOverrideCheck(MESSAGE), declaration);
  }

  private static SyntaxNode record(final SyntaxNode member) {
    return recordWithHeader(
        n("recordHeader", n("formalParameterList", n("recordComponent", t(IDENTIFIER, NAME)))),
        member);
  }

  private static SyntaxNode recordWithHeader(final SyntaxNode header, final SyntaxNode member) {
    return n("recordDeclaration", header, n("recordBody", member));
  }

  private static SyntaxNode classMember(final SyntaxNode method, final boolean override) {
    final String text = override ? "@Override" : "@Nothing";
    return n(
        "classBodyDeclaration", n("memberDeclaration", method), n("modifier", t("modifier", text)));
  }

  private static SyntaxNode method(final String name, final String parameters) {
    return n("methodDeclaration", t(IDENTIFIER, name), t("formalParameters", parameters));
  }

  private static SyntaxNode bareMethod(final String name) {
    return n("methodDeclaration", t(IDENTIFIER, name));
  }
}
