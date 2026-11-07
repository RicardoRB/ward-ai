package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.NoScopes;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The checks that read the lexical scopes, on hand-made scopes, bindings and references. */
final class ScopedRuleChecksTest {

  private static final SourceUnit UNIT = new SourceUnit("m.py", AnalysisLanguage.PYTHON, "");
  private static final Span TARGET = Nodes.at(10, 1);
  private static final Span BOUND = Nodes.at(10, 1);
  private static final int FUNCTION = 2;
  private static final int MODULE = 1;

  @Test
  void unboundLocalReportsAReadBeforeTheLocalAssignment() {
    final Binding local = ScopeFixtures.binding("x", BindingKind.VARIABLE, BOUND, FUNCTION);
    final NameReference read = ScopeFixtures.reference("x", true, FUNCTION, ReferenceAccess.READ);

    assertThat(
            run(
                new UnboundLocalCheck("local '{value}' used early"),
                ScopeFixtures.functionScopes(local, read),
                Nodes.leaf("")))
        .extracting(RuleMatch::message)
        .containsExactly("local 'x' used early");
  }

  @Test
  void unboundLocalIgnoresReadsAfterTheAssignmentAndNonFunctionScopes() {
    final Binding local = ScopeFixtures.binding("x", BindingKind.VARIABLE, BOUND, FUNCTION);

    assertThat(
            run(
                new UnboundLocalCheck("m"),
                ScopeFixtures.functionScopes(
                    local, ScopeFixtures.reference("x", false, FUNCTION, ReferenceAccess.READ)),
                Nodes.leaf("")))
        .isEmpty();
    assertThat(
            run(
                new UnboundLocalCheck("m"),
                ScopeFixtures.functionScopes(
                    local, ScopeFixtures.reference("x", true, MODULE, ReferenceAccess.READ)),
                Nodes.leaf("")))
        .isEmpty();
    assertThat(
            run(
                new UnboundLocalCheck("m"),
                ScopeFixtures.functionScopes(
                    local, ScopeFixtures.reference("x", true, FUNCTION, ReferenceAccess.WRITE)),
                Nodes.leaf("")))
        .isEmpty();
  }

  @Test
  void loopClosureReportsAClosureReadingTheLoopVariable() {
    final Binding loopVar = ScopeFixtures.binding("i", BindingKind.VARIABLE, BOUND, MODULE);
    final NameReference inside = ScopeFixtures.resolvedReference("i", FUNCTION, loopVar);

    assertThat(
            run(
                new LoopClosureCheck("bound '{value}' late"),
                ScopeFixtures.loopScopes(loopVar, inside),
                forLoop()))
        .extracting(RuleMatch::message)
        .containsExactly("bound 'i' late");
  }

  @Test
  void loopClosureIgnoresReadsInTheBindingScope() {
    final Binding loopVar = ScopeFixtures.binding("i", BindingKind.VARIABLE, BOUND, MODULE);

    assertThat(
            run(
                new LoopClosureCheck("m"),
                ScopeFixtures.loopScopes(
                    loopVar, ScopeFixtures.resolvedReference("i", MODULE, loopVar)),
                forLoop()))
        .isEmpty();
  }

  @Test
  void undefinedExportReportsAnExportThatTheModuleDoesNotBind() {
    assertThat(
            run(
                new UndefinedExportCheck("'{value}' not defined"),
                ScopeFixtures.moduleScopes(false),
                exportStatement("\"missing\"")))
        .extracting(RuleMatch::message)
        .containsExactly("'missing' not defined");
  }

  @Test
  void undefinedExportAcceptsAnExportThatTheModuleBindsAndLeavesAStarImportAlone() {
    final Binding os = ScopeFixtures.binding("os", BindingKind.IMPORT, BOUND, MODULE);

    assertThat(
            run(
                new UndefinedExportCheck("m"),
                ScopeFixtures.moduleScopes(false, os),
                exportStatement("\"os\"")))
        .isEmpty();
    assertThat(
            run(
                new UndefinedExportCheck("m"),
                ScopeFixtures.moduleScopes(true),
                exportStatement("\"x\"")))
        .isEmpty();
  }

  private static List<RuleMatch> run(
      final RuleCheck check, final UnitScopes scopes, final SyntaxNode root) {
    return check.matches(
        root, null, new ScopeView(scopes, Map.of(), Set.of(), NoScopes.INSTANCE), UNIT);
  }

  /** {@code for i in items: ...}, with the target at {@link #TARGET}. */
  private static SyntaxNode forLoop() {
    final SyntaxNode target =
        new SyntaxNode("exprlist", TARGET, Map.of(), List.of(Nodes.leaf("i")));
    return Nodes.node(
        "for_stmt",
        Nodes.leaf("for"),
        target,
        Nodes.leaf("in"),
        Nodes.leaf("items"),
        Nodes.leaf(":"));
  }

  /** {@code __all__ = [<string>]} at the module level. */
  private static SyntaxNode exportStatement(final String string) {
    return Nodes.node(
        "file_input",
        Nodes.statement(
            Nodes.node(
                "expr_stmt",
                Nodes.leaf("__all__"),
                Nodes.leaf("="),
                Nodes.leaf("["),
                Nodes.leaf(string),
                Nodes.leaf("]"))));
  }
}
