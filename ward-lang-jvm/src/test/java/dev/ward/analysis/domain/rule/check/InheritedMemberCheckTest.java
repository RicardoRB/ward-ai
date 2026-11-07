package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.InheritedMembers;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InheritedMemberCheckTest {

  private static final AntlrLanguageFrontend FRONTEND = new AntlrLanguageFrontend();
  private static final String BASE_NAME = "app.Base";
  private static final TypeRef BASE = TypeRef.of(BASE_NAME);
  private static final TypeRef VOID = TypeRef.of("void");

  private static CallTarget method(
      final String name, final boolean isStatic, final TypeRef... parameters) {
    return new CallTarget(BASE, name, List.of(parameters), VOID, isStatic, false);
  }

  private static List<String> run(
      final InheritedMemberCheck.Mode mode,
      final String source,
      final InheritedMembers inherited,
      final String superType) {
    final SourceUnit unit = new SourceUnit("A.java", AnalysisLanguage.JAVA, source);
    final SyntaxNode tree = FRONTEND.parse(unit).tree();
    final SyntaxNode type =
        SyntaxWalk.descendants(tree).stream()
            .filter(node -> "classDeclaration".equals(node.kind()))
            .findFirst()
            .orElseThrow();
    final TypeShape shape =
        new TypeShape(TypeShape.Kind.CLASS, false, false, false, List.of(TypeRef.of(superType)));
    final SpanFacts facts =
        new SpanFacts(
            null, shape, null, List.of(), List.of(), List.of(), List.of(), null, inherited);
    final List<RuleMatch> found =
        new InheritedMemberCheck(mode, "message")
            .matches(
                tree,
                FlowView.NONE,
                ScopeView.NONE,
                new UnitTypeFacts(Map.of(UnitTypeFacts.key(type.span()), facts)),
                unit);
    return found.stream().map(m -> m.span().startLine() + ":" + m.span().startColumn()).toList();
  }

  private static InheritedMembers methods(final CallTarget... targets) {
    return new InheritedMembers(List.of(), List.of(targets));
  }

  @Test
  void declaresTheNodeAndFactKindsItReads() {
    final InheritedMemberCheck check =
        new InheritedMemberCheck(InheritedMemberCheck.Mode.FIELD_MASKS, "message");

    assertThat(check.needs().nodeKinds()).containsExactly("classDeclaration");
    assertThat(check.needs().facts()).containsExactlyInAnyOrder(FactKind.MEMBERS, FactKind.SHAPE);
    assertThat(
            check.matches(
                FRONTEND
                    .parse(new SourceUnit("A.java", AnalysisLanguage.JAVA, "class A {}"))
                    .tree()))
        .isEmpty();
  }

  @Test
  void aFieldWithTheNameOfAnInheritedFieldIsReported() {
    final InheritedMembers inherited =
        new InheritedMembers(List.of(new InheritedMembers.Field(BASE, "count", false)), List.of());
    final String source = "class A extends Base {\n  int count;\n  int other;\n}\n";

    assertThat(run(InheritedMemberCheck.Mode.FIELD_MASKS, source, inherited, BASE_NAME))
        .containsExactly("2:7");
    assertThat(run(InheritedMemberCheck.Mode.FIELD_MASKS, source, InheritedMembers.NONE, BASE_NAME))
        .isEmpty();
  }

  @Test
  void aStaticMethodWithTheSignatureOfAnInheritedStaticMethodIsReported() {
    final InheritedMembers inherited =
        methods(method("util", true, TypeRef.of("java.lang.String")), method("run", false));
    final String hiding = "class A extends Base {\n  static void util(String s) {}\n}\n";

    assertThat(run(InheritedMemberCheck.Mode.STATIC_HIDING, hiding, inherited, BASE_NAME))
        .containsExactly("2:10");
    assertThat(
            run(
                InheritedMemberCheck.Mode.STATIC_HIDING,
                "class A extends Base {\n  static void util(int s) {}\n  static void run(String s) {}\n  void util(String s) {}\n}\n",
                inherited,
                BASE_NAME))
        .isEmpty();
  }

  @Test
  void aMethodNamedLikeAnotherVisibleMethodExceptForCaseIsReported() {
    final InheritedMembers inherited = methods(method("getValue", false));
    final String source = "class A extends Base {\n  void getvalue() {}\n  void getValue() {}\n}\n";

    assertThat(run(InheritedMemberCheck.Mode.CASE_CONFUSION, source, inherited, BASE_NAME))
        .containsExactly("2:3", "3:3");
    assertThat(
            run(
                InheritedMemberCheck.Mode.CASE_CONFUSION,
                "class A {\n  void run() {}\n  void RUN() {}\n}\n",
                InheritedMembers.NONE,
                "java.lang.Object"))
        .containsExactly("3:3");
  }

  @Test
  void aMethodThatOverloadsAnAdapterMethodIsReported() {
    final InheritedMembers inherited =
        methods(method("mouseClicked", false, TypeRef.of("java.awt.event.MouseEvent")));
    final String overload =
        "class A extends MouseAdapter {\n  void mouseClicked(KeyEvent e) {}\n}\n";
    final String override =
        "class A extends MouseAdapter {\n  void mouseClicked(MouseEvent e) {}\n}\n";

    assertThat(
            run(
                InheritedMemberCheck.Mode.ADAPTER_OVERLOAD,
                overload,
                inherited,
                "java.awt.event.MouseAdapter"))
        .containsExactly("2:3");
    assertThat(
            run(
                InheritedMemberCheck.Mode.ADAPTER_OVERLOAD,
                override,
                inherited,
                "java.awt.event.MouseAdapter"))
        .isEmpty();
    assertThat(run(InheritedMemberCheck.Mode.ADAPTER_OVERLOAD, overload, inherited, BASE_NAME))
        .isEmpty();
    assertThat(
            run(
                InheritedMemberCheck.Mode.ADAPTER_OVERLOAD,
                "class A extends MouseAdapter {\n  void other(KeyEvent e) {}\n}\n",
                inherited,
                "java.awt.event.MouseAdapter"))
        .isEmpty();
  }

  @Test
  void arraysAndGenericsAreComparedBySimpleName() {
    final InheritedMembers inherited =
        methods(method("fill", true, new TypeRef("java.lang.String", List.of(), 1, false)));

    assertThat(
            run(
                InheritedMemberCheck.Mode.STATIC_HIDING,
                "class A extends Base {\n  static void fill(java.lang.String[] a) {}\n}\n",
                inherited,
                BASE_NAME))
        .hasSize(1);
    assertThat(DeclaredMethods.simpleName("java.util.List<java.util.Map<String, String>>"))
        .isEqualTo("List");
  }
}
