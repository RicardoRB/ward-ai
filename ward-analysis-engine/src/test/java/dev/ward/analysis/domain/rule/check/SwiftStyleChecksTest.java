package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ClassScopes;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SwiftStyleChecksTest {

  private static final int VALUE_3 = 3;
  private static final int VALUE_5 = 5;
  private static final int VALUE_6 = 6;
  private static final int VALUE_10 = 10;
  private static final int VALUE_11 = 11;
  private static final int VALUE_15 = 15;
  private static final int VALUE_20 = 20;
  private static final int VALUE_21 = 21;
  private static final int VALUE_25 = 25;
  private static final int VALUE_30 = 30;
  private static final int VALUE_40 = 40;
  private static final int VALUE_41 = 41;
  private static final int VALUE_42 = 42;
  private static final int VALUE_43 = 43;
  private static final int VALUE_45 = 45;
  private static final int VALUE_46 = 46;
  private static final int VALUE_47 = 47;
  private static final int VALUE_50 = 50;
  private static final int VALUE_51 = 51;
  private static final int VALUE_60 = 60;
  private static final int VALUE_61 = 61;
  private static final int VALUE_65 = 65;
  private static final int VALUE_80 = 80;
  private static final int VALUE_90 = 90;
  private static final int VALUE_100 = 100;

  private static final SyntaxNode ROOT = node("file", 0, 0);
  private static final SourceUnit EMPTY = new SourceUnit("m.swift", AnalysisLanguage.SWIFT, "");
  private static final String INIT_TEXT = "init(x: Int) {}";

  private static Span span(final int start, final int end) {
    return new Span(1, start + 1, 1, end + 1, start, end);
  }

  private static SyntaxNode node(
      final String kind, final int start, final int end, final SyntaxNode... children) {
    return new SyntaxNode(kind, span(start, end), Map.of(), List.of(children));
  }

  private static SyntaxNode leaf(final String kind, final String text, final int start) {
    return new SyntaxNode(
        kind, span(start, start + text.length()), Map.of("text", text), List.of());
  }

  private static ScopeView view(final UnitScopes scopes) {
    return new ScopeView(
        scopes,
        Map.of(),
        Set.of(),
        new ScopeAnalysis() {
          @Override
          public UnitScopes analyze(final SourceUnit unit, final SyntaxNode tree) {
            return scopes;
          }

          @Override
          public Set<String> builtins(final Set<String> environments) {
            return Set.of();
          }
        });
  }

  private static List<RuleMatch> hiddenField(final UnitScopes scopes, final SourceUnit unit) {
    return new HiddenFieldCheck("{value} hides").matches(ROOT, FlowView.NONE, view(scopes), unit);
  }

  private static UnitScopes memberThenParameter(final String memberName, final String paramName) {
    final ScopeBuilder builder = new ScopeBuilder(span(0, VALUE_100), ClassScopes.VISIBLE);
    builder.open(ScopeKind.CLASS, span(0, VALUE_90));
    builder.declare(memberName, BindingKind.VARIABLE, span(VALUE_10, VALUE_11), Set.of());
    builder.open(ScopeKind.FUNCTION, span(VALUE_51, VALUE_80));
    builder.declare(paramName, BindingKind.PARAMETER, span(VALUE_60, VALUE_61), Set.of());
    builder.close();
    builder.close();
    return builder.build();
  }

  @Test
  void hiddenFieldReportsParameterNamedLikeMemberOfEnclosingType() {
    final List<RuleMatch> found = hiddenField(memberThenParameter("x", "x"), EMPTY);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("x hides");
    assertThat(found.get(0).span().startOffset()).isEqualTo(VALUE_60);
  }

  @Test
  void hiddenFieldIgnoresInitializerParameters() {
    final SourceUnit init = new SourceUnit("m.swift", AnalysisLanguage.SWIFT, INIT_TEXT);
    final ScopeBuilder builder = new ScopeBuilder(span(0, VALUE_100), ClassScopes.VISIBLE);
    builder.open(ScopeKind.CLASS, span(0, VALUE_90));
    builder.declare("x", BindingKind.VARIABLE, span(VALUE_10, VALUE_11), Set.of());
    builder.open(ScopeKind.FUNCTION, span(0, VALUE_15));
    builder.declare("x", BindingKind.PARAMETER, span(VALUE_5, VALUE_6), Set.of());
    builder.close();
    builder.close();

    assertThat(hiddenField(builder.build(), init)).isEmpty();
  }

  @Test
  void hiddenFieldReportsLocalNamedLikeMember() {
    final ScopeBuilder builder = new ScopeBuilder(span(0, VALUE_100), ClassScopes.VISIBLE);
    builder.open(ScopeKind.CLASS, span(0, VALUE_90));
    builder.declare("count", BindingKind.VARIABLE, span(VALUE_10, VALUE_15), Set.of());
    builder.open(ScopeKind.BLOCK, span(VALUE_51, VALUE_80));
    builder.declare("count", BindingKind.CONSTANT, span(VALUE_60, VALUE_65), Set.of());
    builder.close();
    builder.close();

    assertThat(hiddenField(builder.build(), EMPTY)).hasSize(1);
  }

  @Test
  void hiddenFieldIsSilentWithoutAnEnclosingMemberOfTheSameName() {
    assertThat(hiddenField(memberThenParameter("y", "x"), EMPTY)).isEmpty();
  }

  @Test
  void hiddenFieldNeedsTheScopes() {
    assertThatThrownBy(() -> new HiddenFieldCheck("m").matches(ROOT))
        .isInstanceOf(AnalysisFailedException.class);
  }

  private static UnitScopes useAfterDeclaration(final int use) {
    final ScopeBuilder builder = new ScopeBuilder(span(0, VALUE_60), ClassScopes.VISIBLE);
    builder.open(ScopeKind.BLOCK, span(0, VALUE_60));
    builder.declare("total", BindingKind.VARIABLE, span(2, VALUE_5), Set.of());
    builder.reference("total", span(use, use + 1), ReferenceAccess.READ);
    builder.close();
    return builder.build();
  }

  private static SyntaxNode fiveStatements() {
    return node(
        "file",
        0,
        VALUE_60,
        node("statement", 0, VALUE_10),
        node("statement", VALUE_10, VALUE_20),
        node("statement", VALUE_20, VALUE_30),
        node("statement", VALUE_30, VALUE_40),
        node("statement", VALUE_40, VALUE_50));
  }

  private static List<RuleMatch> distance(final int max, final UnitScopes scopes) {
    return new VariableDistanceCheck(max, "{value} read after {max}")
        .matches(fiveStatements(), FlowView.NONE, view(scopes), EMPTY);
  }

  @Test
  void variableDistanceReportsFirstReadBeyondTheLimit() {
    final List<RuleMatch> found = distance(2, useAfterDeclaration(VALUE_42));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("total read after 2");
    assertThat(found.get(0).span().startOffset()).isEqualTo(2);
  }

  @Test
  void variableDistanceAcceptsAFirstReadWithinTheLimit() {
    assertThat(distance(VALUE_3, useAfterDeclaration(VALUE_42))).isEmpty();
  }

  @Test
  void variableDistanceIgnoresReadsThatDoNotFollowTheDeclaration() {
    assertThat(distance(0, useAfterDeclaration(0))).isEmpty();
  }

  @Test
  void variableDistanceIgnoresConstants() {
    final ScopeBuilder builder = new ScopeBuilder(span(0, VALUE_60), ClassScopes.VISIBLE);
    builder.open(ScopeKind.BLOCK, span(0, VALUE_60));
    builder.declare("total", BindingKind.CONSTANT, span(2, VALUE_5), Set.of());
    builder.reference("total", span(VALUE_42, VALUE_43), ReferenceAccess.READ);
    builder.close();

    assertThat(distance(0, builder.build())).isEmpty();
  }

  @Test
  void variableDistanceNeedsTheScopes() {
    assertThatThrownBy(() -> new VariableDistanceCheck(1, "m").matches(ROOT))
        .isInstanceOf(AnalysisFailedException.class);
  }

  private static SyntaxNode documentedFunction(final String comment, final String... parameters) {
    return documentedFunction(comment, false, false, parameters);
  }

  private static SyntaxNode documentedFunction(
      final String comment,
      final boolean returns,
      final boolean throwing,
      final String... parameters) {
    final SyntaxNode[] params = new SyntaxNode[parameters.length];
    for (int index = 0; index < parameters.length; index++) {
      params[index] =
          node(
              "parameter",
              VALUE_20 + index,
              VALUE_25 + index,
              node(
                  "local_parameter_name",
                  VALUE_20 + index,
                  VALUE_21 + index,
                  leaf("label_identifier", parameters[index], VALUE_20 + index)));
    }
    final SyntaxNode[] signature = new SyntaxNode[params.length + 2];
    System.arraycopy(params, 0, signature, 0, params.length);
    signature[params.length] =
        returns ? node("function_result", VALUE_40, VALUE_45) : node("filler", VALUE_40, VALUE_41);
    signature[params.length + 1] =
        throwing ? node("throws_clause", VALUE_46, VALUE_50) : node("filler", VALUE_46, VALUE_47);
    final SyntaxNode function =
        node(
            "function_declaration",
            VALUE_60,
            VALUE_100,
            node("function_signature", VALUE_15, VALUE_90, signature));
    final SyntaxNode doc =
        new SyntaxNode("comment", span(0, VALUE_10), Map.of("text", comment), List.of());
    return node("file", 0, VALUE_100, doc, function);
  }

  private static List<RuleMatch> javadoc(final SyntaxNode file) {
    return new JavadocMethodCheck("missing {value}").matches(file);
  }

  @Test
  void javadocMethodReportsMissingParameterTag() {
    final List<RuleMatch> found =
        javadoc(documentedFunction("/** - Parameter a: first */", "a", "b"));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("missing - Parameter b:");
  }

  @Test
  void javadocMethodReportsMissingReturnAndThrowsTags() {
    final List<RuleMatch> found =
        javadoc(documentedFunction("/** - Parameter a: first */", true, true, "a"));

    assertThat(found)
        .extracting(RuleMatch::message)
        .containsExactly("missing - Returns:", "missing - Throws:");
  }

  @Test
  void javadocMethodAcceptsAFullyDocumentedFunction() {
    assertThat(
            javadoc(
                documentedFunction(
                    "/** - Parameter a: first - Returns: sum - Throws: never */", true, true, "a")))
        .isEmpty();
  }

  @Test
  void javadocMethodLeavesUndocumentedFunctionsToTheMissingDocumentationRule() {
    final SyntaxNode undocumented =
        node(
            "file",
            0,
            VALUE_100,
            node(
                "function_declaration",
                VALUE_60,
                VALUE_100,
                node(
                    "function_signature",
                    VALUE_15,
                    VALUE_90,
                    node(
                        "parameter",
                        VALUE_20,
                        VALUE_25,
                        node(
                            "local_parameter_name",
                            VALUE_20,
                            VALUE_21,
                            leaf("label_identifier", "a", VALUE_20))))));

    assertThat(javadoc(undocumented)).isEmpty();
  }

  private static SyntaxNode member(final String text, final int start) {
    return node("class_member", start, start + text.length(), leaf("token", text, start));
  }

  private static List<RuleMatch> grouping(final SyntaxNode... members) {
    return new ConstructorGroupCheck("{value} misplaced")
        .matches(node("file", 0, VALUE_100, node("struct_body", 0, VALUE_100, members)));
  }

  @Test
  void constructorGroupReportsAnInitializerAfterAnotherMember() {
    final List<RuleMatch> found =
        grouping(
            member("init(x:Int){}", 1),
            member("func scale(){}", VALUE_20),
            member("init(){}", VALUE_40));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).span().startOffset()).isEqualTo(VALUE_40);
  }

  @Test
  void constructorGroupAcceptsGroupedInitializers() {
    assertThat(
            grouping(
                member("@objcinit(x:Int){}", 1),
                member("publicinit(){}", VALUE_20),
                member("var y=0", VALUE_40)))
        .isEmpty();
  }

  private static SyntaxNode importNode(final String name, final int start) {
    return node(
        "import_declaration",
        start,
        start + VALUE_6 + name.length(),
        leaf("token", "import" + name, start));
  }

  private static List<RuleMatch> imports(final SyntaxNode... imports) {
    return new ImportSortCheck("{value} unsorted").matches(node("file", 0, VALUE_100, imports));
  }

  @Test
  void importSortReportsAnImportBeforeItsPredecessor() {
    final List<RuleMatch> found =
        imports(importNode("Foundation", 0), importNode("Combine", VALUE_30));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("Combine unsorted");
  }

  @Test
  void importSortIgnoresAttributesBeforeTheKeyword() {
    final SyntaxNode testable =
        node("import_declaration", 0, VALUE_20, leaf("token", "@testableimportShop", 0));

    assertThat(imports(importNode("Combine", VALUE_30), testable)).isEmpty();
  }

  @Test
  void importSortAcceptsSortedImports() {
    assertThat(imports(importNode("Combine", 0), importNode("Foundation", VALUE_30))).isEmpty();
  }
}
