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
import dev.ward.analysis.domain.types.FieldRef;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InheritedMemberCheckTest {

  private static final AntlrLanguageFrontend FRONTEND = new AntlrLanguageFrontend();
  private static final String BASE_NAME = "app.Base";
  private static final TypeRef BASE = TypeRef.of(BASE_NAME);
  private static final TypeMembers NOTHING = methods();
  private static final TypeRef VOID = TypeRef.of("void");

  private static CallTarget method(
      final String name, final boolean isStatic, final TypeRef... parameters) {
    return new CallTarget(
        BASE,
        name,
        List.of(parameters),
        VOID,
        isStatic,
        false,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static List<String> run(
      final InheritedMemberCheck.Mode mode,
      final String source,
      final TypeMembers inherited,
      final String superType) {
    final SourceUnit unit = new SourceUnit("A.java", AnalysisLanguage.JAVA, source);
    final SyntaxNode tree = FRONTEND.parse(unit).tree();
    final SyntaxNode type =
        SyntaxWalk.descendants(tree).stream()
            .filter(node -> "classDeclaration".equals(node.kind()))
            .findFirst()
            .orElseThrow();
    final TypeMembers members =
        new TypeMembers(
            List.of(),
            List.of(),
            List.of(TypeRef.of(superType)),
            List.of(),
            inherited.inheritedMethods(),
            inherited.inheritedFields());
    final SpanFacts facts =
        new SpanFacts(null, null, null, List.of(), List.of(), List.of(), List.of(), null, members);
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

  private static TypeMembers methods(final CallTarget... targets) {
    return new TypeMembers(List.of(), List.of(), List.of(), List.of(), List.of(targets), List.of());
  }

  @Test
  void declaresTheNodeAndFactKindsItReads() {
    final InheritedMemberCheck check =
        new InheritedMemberCheck(InheritedMemberCheck.Mode.FIELD_MASKS, "message");

    assertThat(check.needs().nodeKinds()).containsExactly("classDeclaration");
    assertThat(check.needs().facts()).containsExactly(FactKind.MEMBERS);
    assertThat(
            check.matches(
                FRONTEND
                    .parse(new SourceUnit("A.java", AnalysisLanguage.JAVA, "class A {}"))
                    .tree()))
        .isEmpty();
  }

  @Test
  void aFieldWithTheNameOfAnInheritedFieldIsReported() {
    final TypeMembers inherited =
        new TypeMembers(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new FieldRef("count", TypeRef.of("int"), false, List.of())));
    final String source = "class A extends Base {\n  int count;\n  int other;\n}\n";

    assertThat(run(InheritedMemberCheck.Mode.FIELD_MASKS, source, inherited, BASE_NAME))
        .containsExactly("2:7");
    assertThat(run(InheritedMemberCheck.Mode.FIELD_MASKS, source, NOTHING, BASE_NAME)).isEmpty();
  }

  @Test
  void aStaticMethodWithTheSignatureOfAnInheritedStaticMethodIsReported() {
    final TypeMembers inherited =
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
    final TypeMembers inherited = methods(method("getValue", false));
    final String source = "class A extends Base {\n  void getvalue() {}\n  void getValue() {}\n}\n";

    assertThat(run(InheritedMemberCheck.Mode.CASE_CONFUSION, source, inherited, BASE_NAME))
        .containsExactly("2:3", "3:3");
    assertThat(
            run(
                InheritedMemberCheck.Mode.CASE_CONFUSION,
                "class A {\n  void run() {}\n  void RUN() {}\n}\n",
                NOTHING,
                "java.lang.Object"))
        .containsExactly("3:3");
  }

  @Test
  void aMethodThatOverloadsAnAdapterMethodIsReported() {
    final TypeMembers inherited =
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
    final TypeMembers inherited =
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
