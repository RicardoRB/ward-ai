package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorProneClassChecksTest extends ClassMemberTrees {

  private static final String MESSAGE = "m";
  private static final String GET_INSTANCE = "getInstance";
  private static final String FOR_CONTROL = "forControl";
  private static final String EXPRESSION_LIST = "expressionList";

  private List<RuleMatch> run(final UnitCheck check, final SyntaxNode... top) {
    return check.check(compilationUnit(top), SourceFile.UNKNOWN);
  }

  // member-named-after-class

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

  // return-empty-collection

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

    assertThat(run(new ReturnEmptyCollectionCheck(MESSAGE), clazz)).hasSize(3);
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

  // static-ejb-field

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
        .hasSize(3);
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

  // single-method-singleton

  @Test
  void reportsEveryGetInstanceAfterTheFirst() {
    final SyntaxNode clazz =
        classNamed(
            CLASS_NAME,
            member(method(GET_INSTANCE, 0, 0)),
            member(method(GET_INSTANCE, 1, 0)),
            member(method(GET_INSTANCE, 2, 0)));

    assertThat(run(new SingleMethodSingletonCheck(MESSAGE), clazz)).hasSize(2);
  }

  @Test
  void skipsASingleGetInstance() {
    final SyntaxNode clazz =
        classNamed(CLASS_NAME, member(method(GET_INSTANCE, 0, 0)), member(method("other", 0, 0)));

    assertThat(run(new SingleMethodSingletonCheck(MESSAGE), clazz)).isEmpty();
  }

  // jumbled-incrementer

  private SyntaxNode forLoop(final String variable, final String update, final SyntaxNode... body) {
    final SyntaxNode init =
        node(
            "forInit",
            LINE,
            COLUMN,
            node(
                "localVariableDeclaration",
                LINE,
                COLUMN,
                node(VARIABLE_DECLARATOR_ID, LINE, COLUMN, ident(variable))));
    final SyntaxNode updateList =
        node(
            EXPRESSION_LIST,
            LINE,
            COLUMN,
            leaf(TOKEN_IDENTIFIER, update.split("\\|")[0], LINE, COLUMN),
            leaf("OP", update.split("\\|")[1], LINE, COLUMN));
    return node(
        "statement",
        LINE,
        COLUMN,
        leaf("'for'", "for", LINE, COLUMN),
        node(FOR_CONTROL, LINE, COLUMN, init, leaf("';'", ";", LINE, COLUMN), updateList),
        node(BLOCK, LINE, COLUMN, body));
  }

  @Test
  void reportsInnerLoopThatUpdatesTheOuterVariable() {
    final SyntaxNode inner = forLoop("j", "i|++");
    final SyntaxNode outer = forLoop("i", "i|++", inner);

    assertThat(run(new JumbledIncrementerCheck(MESSAGE), outer)).hasSize(1);
    assertThat(run(new JumbledIncrementerCheck(MESSAGE), forLoop("i", "x|+=", inner)))
        .hasSize(1);
  }

  @Test
  void skipsLoopsThatUpdateTheirOwnVariable() {
    final SyntaxNode outer = forLoop("i", "i|++", forLoop("j", "j|++"));
    final SyntaxNode compound = forLoop("i", "i|--", forLoop("i", "i|+="));
    final SyntaxNode comparison = forLoop("i", "i|++", forLoop("j", "k|=="));
    final SyntaxNode noUpdate =
        node(
            "statement",
            LINE,
            COLUMN,
            node(FOR_CONTROL, LINE, COLUMN, leaf("';'", ";", LINE, COLUMN)));

    assertThat(run(new JumbledIncrementerCheck(MESSAGE), outer)).isEmpty();
    assertThat(run(new JumbledIncrementerCheck(MESSAGE), compound)).isEmpty();
    assertThat(run(new JumbledIncrementerCheck(MESSAGE), comparison)).isEmpty();
    assertThat(run(new JumbledIncrementerCheck(MESSAGE), noUpdate)).isEmpty();
  }
}
