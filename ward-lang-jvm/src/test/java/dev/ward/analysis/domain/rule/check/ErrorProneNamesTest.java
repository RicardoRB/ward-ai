package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

class ErrorProneNamesTest extends ErrorProneTrees {

  @Test
  void reportsFieldAndMethodNamedAfterTheirClassIgnoringCase() {
    final SyntaxNode clazz =
        classNamed(
            CLASS_NAME,
            member(field("sample")),
            member(method("SAMPLE", 0, 0)),
            member(constructorWith(0)),
            member(field("other")));

    assertThat(run(new MemberNamedAfterClassCheck("field", MESSAGE), clazz)).hasSize(1);
    assertThat(run(new MemberNamedAfterClassCheck("method", MESSAGE), clazz)).hasSize(1);
  }

  @Test
  void skipsMembersWithOtherNamesAndRejectsUnknownMemberKind() {
    final SyntaxNode clazz = classNamed(CLASS_NAME, member(field("a")), member(method("b", 0, 0)));

    assertThat(run(new MemberNamedAfterClassCheck("field", MESSAGE), clazz)).isEmpty();
    assertThat(run(new MemberNamedAfterClassCheck("method", MESSAGE), clazz)).isEmpty();
    assertThatThrownBy(() -> new MemberNamedAfterClassCheck("class", MESSAGE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private SyntaxNode returnNull() {
    return node(
        "statement",
        LINE,
        COLUMN,
        leaf("'return'", "return", LINE, COLUMN),
        leaf("'null'", "null", LINE, COLUMN),
        leaf("';'", ";", LINE, COLUMN));
  }

  private SyntaxNode returning(final String type, final SyntaxNode... body) {
    return node(
        "methodDeclaration",
        LINE,
        COLUMN,
        node(TYPE_TYPE, LINE, COLUMN, leaf(TOKEN_IDENTIFIER, type, LINE, COLUMN)),
        ident("find"),
        node(FORMAL_PARAMETERS, LINE, COLUMN),
        node(BLOCK, LINE, COLUMN, body));
  }

  @Test
  void reportsReturnNullFromCollectionAndArrayMethods() {
    final SyntaxNode clazz =
        classNamed(
            CLASS_NAME,
            member(returning("List<String>", returnNull())),
            member(returning("java.util.Map", returnNull())),
            member(returning("int[]", returnNull())));

    assertThat(run(new ReturnEmptyCollectionCheck(MESSAGE), clazz)).hasSize(THREE);
  }

  @Test
  void skipsReturnNullFromOtherTypesAndFromNestedLambdas() {
    final SyntaxNode lambda = node("lambdaExpression", LINE, COLUMN, returnNull());
    final SyntaxNode clazz =
        classNamed(
            CLASS_NAME,
            member(returning("String", returnNull())),
            member(returning("List", lambda)),
            member(method("run", 0, 0)));

    assertThat(run(new ReturnEmptyCollectionCheck(MESSAGE), clazz)).isEmpty();
  }

  private SyntaxNode annotated(final String annotation, final SyntaxNode clazz) {
    return node(
        "typeDeclaration",
        1,
        1,
        node("classOrInterfaceModifier", 1, 1, leaf("ANNOTATION", annotation, 1, 1)),
        clazz);
  }

  @Test
  void reportsStaticNonFinalFieldOfAnnotatedAndImplementingEjbs() {
    final SyntaxNode body = member(field("counter"), STATIC);
    final SyntaxNode implementing =
        node(
            "classDeclaration",
            1,
            1,
            leaf("'class'", "class", 1, 1),
            ident("Impl"),
            node("typeList", 1, 1, ident("SessionBean")),
            node("classBody", 1, 1, body));

    assertThat(
            run(
                new StaticEjbFieldCheck(MESSAGE),
                annotated("@javax.ejb.Stateless", classNamed(CLASS_NAME, body)),
                annotated("@MessageDriven(name=\"x\")", classNamed("Other", body)),
                implementing))
        .hasSize(THREE);
  }

  @Test
  void skipsFinalInstanceAndNonEjbFields() {
    final SyntaxNode finalField = member(field("a"), STATIC, "final");

    assertThat(
            run(
                new StaticEjbFieldCheck(MESSAGE),
                annotated("@Stateless", classNamed(CLASS_NAME, finalField, member(field("b")))),
                annotated("@Other", classNamed("Plain", member(field("c"), STATIC))),
                classNamed("Bare", member(field("d"), STATIC))))
        .isEmpty();
  }
}
