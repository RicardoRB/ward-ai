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
    final ScopeBuilder builder = new ScopeBuilder(span(0, 100), ClassScopes.VISIBLE);
    builder.open(ScopeKind.CLASS, span(0, 90));
    builder.declare(memberName, BindingKind.VARIABLE, span(10, 11), Set.of());
    builder.open(ScopeKind.FUNCTION, span(51, 80));
    builder.declare(paramName, BindingKind.PARAMETER, span(60, 61), Set.of());
    builder.close();
    builder.close();
    return builder.build();
  }

  @Test
  void hiddenFieldReportsParameterNamedLikeMemberOfEnclosingType() {
    final List<RuleMatch> found = hiddenField(memberThenParameter("x", "x"), EMPTY);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("x hides");
    assertThat(found.get(0).span().startOffset()).isEqualTo(60);
  }

  @Test
  void hiddenFieldIgnoresInitializerParameters() {
    final SourceUnit init = new SourceUnit("m.swift", AnalysisLanguage.SWIFT, INIT_TEXT);
    final ScopeBuilder builder = new ScopeBuilder(span(0, 100), ClassScopes.VISIBLE);
    builder.open(ScopeKind.CLASS, span(0, 90));
    builder.declare("x", BindingKind.VARIABLE, span(10, 11), Set.of());
    builder.open(ScopeKind.FUNCTION, span(0, 15));
    builder.declare("x", BindingKind.PARAMETER, span(5, 6), Set.of());
    builder.close();
    builder.close();

    assertThat(hiddenField(builder.build(), init)).isEmpty();
  }

  @Test
  void hiddenFieldReportsLocalNamedLikeMember() {
    final ScopeBuilder builder = new ScopeBuilder(span(0, 100), ClassScopes.VISIBLE);
    builder.open(ScopeKind.CLASS, span(0, 90));
    builder.declare("count", BindingKind.VARIABLE, span(10, 15), Set.of());
    builder.open(ScopeKind.BLOCK, span(51, 80));
    builder.declare("count", BindingKind.CONSTANT, span(60, 65), Set.of());
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
    final ScopeBuilder builder = new ScopeBuilder(span(0, 60), ClassScopes.VISIBLE);
    builder.open(ScopeKind.BLOCK, span(0, 60));
    builder.declare("total", BindingKind.VARIABLE, span(2, 5), Set.of());
    builder.reference("total", span(use, use + 1), ReferenceAccess.READ);
    builder.close();
    return builder.build();
  }

  private static SyntaxNode fiveStatements() {
    return node(
        "file",
        0,
        60,
        node("statement", 0, 10),
        node("statement", 10, 20),
        node("statement", 20, 30),
        node("statement", 30, 40),
        node("statement", 40, 50));
  }

  private static List<RuleMatch> distance(final int max, final UnitScopes scopes) {
    return new VariableDistanceCheck(max, "{value} read after {max}")
        .matches(fiveStatements(), FlowView.NONE, view(scopes), EMPTY);
  }

  @Test
  void variableDistanceReportsFirstReadBeyondTheLimit() {
    final List<RuleMatch> found = distance(2, useAfterDeclaration(42));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("total read after 2");
    assertThat(found.get(0).span().startOffset()).isEqualTo(2);
  }

  @Test
  void variableDistanceAcceptsAFirstReadWithinTheLimit() {
    assertThat(distance(3, useAfterDeclaration(42))).isEmpty();
  }

  @Test
  void variableDistanceIgnoresReadsThatDoNotFollowTheDeclaration() {
    assertThat(distance(0, useAfterDeclaration(0))).isEmpty();
  }

  @Test
  void variableDistanceIgnoresConstants() {
    final ScopeBuilder builder = new ScopeBuilder(span(0, 60), ClassScopes.VISIBLE);
    builder.open(ScopeKind.BLOCK, span(0, 60));
    builder.declare("total", BindingKind.CONSTANT, span(2, 5), Set.of());
    builder.reference("total", span(42, 43), ReferenceAccess.READ);
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
              20 + index,
              25 + index,
              node(
                  "local_parameter_name",
                  20 + index,
                  21 + index,
                  leaf("label_identifier", parameters[index], 20 + index)));
    }
    final SyntaxNode[] signature = new SyntaxNode[params.length + 2];
    System.arraycopy(params, 0, signature, 0, params.length);
    signature[params.length] = returns ? node("function_result", 40, 45) : node("filler", 40, 41);
    signature[params.length + 1] =
        throwing ? node("throws_clause", 46, 50) : node("filler", 46, 47);
    final SyntaxNode function =
        node("function_declaration", 60, 100, node("function_signature", 15, 90, signature));
    final SyntaxNode doc =
        new SyntaxNode("comment", span(0, 10), Map.of("text", comment), List.of());
    return node("file", 0, 100, doc, function);
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
            100,
            node(
                "function_declaration",
                60,
                100,
                node(
                    "function_signature",
                    15,
                    90,
                    node(
                        "parameter",
                        20,
                        25,
                        node("local_parameter_name", 20, 21, leaf("label_identifier", "a", 20))))));

    assertThat(javadoc(undocumented)).isEmpty();
  }

  private static SyntaxNode member(final String text, final int start) {
    return node("class_member", start, start + text.length(), leaf("token", text, start));
  }

  private static List<RuleMatch> grouping(final SyntaxNode... members) {
    return new ConstructorGroupCheck("{value} misplaced")
        .matches(node("file", 0, 100, node("struct_body", 0, 100, members)));
  }

  @Test
  void constructorGroupReportsAnInitializerAfterAnotherMember() {
    final List<RuleMatch> found =
        grouping(member("init(x:Int){}", 1), member("func scale(){}", 20), member("init(){}", 40));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).span().startOffset()).isEqualTo(40);
  }

  @Test
  void constructorGroupAcceptsGroupedInitializers() {
    assertThat(
            grouping(
                member("@objcinit(x:Int){}", 1),
                member("publicinit(){}", 20),
                member("var y=0", 40)))
        .isEmpty();
  }

  private static SyntaxNode importNode(final String name, final int start) {
    return node(
        "import_declaration",
        start,
        start + 6 + name.length(),
        leaf("token", "import" + name, start));
  }

  private static List<RuleMatch> imports(final SyntaxNode... imports) {
    return new ImportSortCheck("{value} unsorted").matches(node("file", 0, 100, imports));
  }

  @Test
  void importSortReportsAnImportBeforeItsPredecessor() {
    final List<RuleMatch> found = imports(importNode("Foundation", 0), importNode("Combine", 30));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("Combine unsorted");
  }

  @Test
  void importSortAcceptsSortedImports() {
    assertThat(imports(importNode("Combine", 0), importNode("Foundation", 30))).isEmpty();
  }
}
