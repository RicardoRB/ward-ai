package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NoScopes;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The checks on annotations, groupby loops and version-gated strict calls, on hand-made trees. */
final class AnnotationAndCallChecksTest {

  private static final SourceUnit UNIT = new SourceUnit("m.py", AnalysisLanguage.PYTHON, "");
  private static final int FUNCTION = 2;
  private static final String ANNOTATION = "annassign";
  private static final String BATCHED = "itertools.batched([1],2)";

  @Test
  void unusedAnnotationReportsABareAnnotationNeverRead() {
    final Binding local =
        ScopeFixtures.binding("y", BindingKind.VARIABLE, Nodes.at(0, 1), FUNCTION);
    final UnitScopes scopes =
        ScopeFixtures.functionScopes(
            local, ScopeFixtures.reference("other", false, FUNCTION, ReferenceAccess.READ));

    assertThat(unusedAnnotation(scopes, annotated(false)))
        .extracting(RuleMatch::message)
        .containsExactly("y unused");
  }

  @Test
  void unusedAnnotationIgnoresReadsValuesAndModuleLevelAnnotations() {
    final Binding local =
        ScopeFixtures.binding("y", BindingKind.VARIABLE, Nodes.at(0, 1), FUNCTION);
    final UnitScopes read =
        ScopeFixtures.functionScopes(local, ScopeFixtures.resolvedReference("y", FUNCTION, local));

    assertThat(unusedAnnotation(read, annotated(false))).isEmpty();
    assertThat(
            unusedAnnotation(
                ScopeFixtures.functionScopes(
                    local, ScopeFixtures.reference("o", false, FUNCTION, ReferenceAccess.READ)),
                annotated(true)))
        .isEmpty();
    assertThat(
            unusedAnnotation(
                ScopeFixtures.moduleScopes(
                    false, ScopeFixtures.binding("y", BindingKind.VARIABLE, Nodes.at(0, 1), 1)),
                annotated(false)))
        .isEmpty();
  }

  @Test
  void groupbyReuseReportsASecondUseOfTheGroup() {
    final SyntaxNode twice = groupbyLoop("groupby([1])", "list", "g", "list", "g");
    final SyntaxNode once = groupbyLoop("groupby([1])", "list", "g");

    assertThat(new GroupbyReuseCheck("m").matches(twice)).hasSize(1);
    assertThat(new GroupbyReuseCheck("m").matches(once)).isEmpty();
    assertThat(new GroupbyReuseCheck("m").matches(groupbyLoop("items", "g", "g"))).isEmpty();
  }

  @Test
  void stringAnnotationReportsTextThatCannotBeAnExpression() {
    final StringAnnotationCheck check = new StringAnnotationCheck("m");

    assertThat(
            check.matches(
                Nodes.node("tfpdef", Nodes.leaf("x"), Nodes.leaf(":"), Nodes.leaf("\"int +\""))))
        .hasSize(1);
    assertThat(check.matches(Nodes.node(ANNOTATION, Nodes.leaf(":"), Nodes.leaf("\"list[int\""))))
        .hasSize(1);
    assertThat(check.matches(Nodes.node("funcdef", Nodes.leaf("->"), Nodes.leaf("\"\""))))
        .hasSize(1);
    assertThat(check.matches(Nodes.node("funcdef", Nodes.leaf("->"), Nodes.leaf("\")\""))))
        .hasSize(1);
  }

  @Test
  void stringAnnotationAcceptsExpressionsAndIgnoresOtherColons() {
    final StringAnnotationCheck check = new StringAnnotationCheck("m");

    assertThat(
            check.matches(
                Nodes.node(
                    "tfpdef", Nodes.leaf("x"), Nodes.leaf(":"), Nodes.leaf("\"list[int]\""))))
        .isEmpty();
    assertThat(check.matches(Nodes.node(ANNOTATION, Nodes.leaf(":"), Nodes.leaf("name"))))
        .isEmpty();
    assertThat(check.matches(Nodes.node("funcdef", Nodes.leaf(":"), Nodes.leaf("\"doc +\""))))
        .isEmpty();
  }

  @Test
  void strictCallReportsACallOnlyWhenTheTargetReachesTheVersion() {
    final StrictCallCheck.Rule batched =
        new StrictCallCheck.Rule(Pattern.compile("^(itertools\\.)?batched\\("), 13, 0);

    assertThat(new StrictCallCheck(batched, "py313", "m").matches(Shapes.call(BATCHED))).hasSize(1);
    assertThat(new StrictCallCheck(batched, "py310", "m").matches(Shapes.call(BATCHED))).isEmpty();
    assertThat(
            new StrictCallCheck(batched, "py313", "m")
                .matches(Shapes.call("itertools.batched([1],2,strict=True)")))
        .isEmpty();
    assertThat(new StrictCallCheck(batched, "py313", "m").matches(Shapes.call("other([1],2)")))
        .isEmpty();
  }

  @Test
  void strictCallJudgesMapByItsArgumentsAndRejectsABadTarget() {
    final StrictCallCheck.Rule map = new StrictCallCheck.Rule(Pattern.compile("^map\\("), 14, 3);
    final StrictCallCheck check = new StrictCallCheck(map, "py314", "m");

    assertThat(check.matches(Shapes.call("map(str,[1],[2])"))).hasSize(1);
    assertThat(check.matches(Shapes.call("map(f,*a)"))).hasSize(1);
    assertThat(check.matches(Shapes.call("map(str,[1])"))).isEmpty();
    assertThatThrownBy(() -> new StrictCallCheck(map, "3.14", "m"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static List<RuleMatch> unusedAnnotation(final UnitScopes scopes, final SyntaxNode root) {
    return new UnusedAnnotationCheck("{value} unused")
        .matches(root, null, new ScopeView(scopes, Map.of(), Set.of(), NoScopes.INSTANCE), UNIT);
  }

  /** {@code y: int}, or {@code y: int = 1} when {@code withValue}. */
  private static SyntaxNode annotated(final boolean withValue) {
    final SyntaxNode annotation =
        withValue
            ? Nodes.node(
                ANNOTATION, Nodes.leaf(":"), Nodes.leaf("int"), Nodes.leaf("="), Nodes.leaf("1"))
            : Nodes.node(ANNOTATION, Nodes.leaf(":"), Nodes.leaf("int"));
    return Nodes.node("expr_stmt", Nodes.leaf("y"), annotation);
  }

  /** {@code for k, g in <iterable>: <body>}. */
  private static SyntaxNode groupbyLoop(final String iterable, final String... body) {
    final SyntaxNode[] tokens = new SyntaxNode[body.length];
    for (int index = 0; index < body.length; index++) {
      tokens[index] = Nodes.leaf(body[index]);
    }
    return Nodes.node(
        "for_stmt",
        Nodes.leaf("for"),
        Nodes.node("exprlist", Nodes.leaf("k"), Nodes.leaf(","), Nodes.leaf("g")),
        Nodes.leaf("in"),
        Nodes.leaf(iterable),
        Nodes.leaf(":"),
        Nodes.block(tokens));
  }
}
