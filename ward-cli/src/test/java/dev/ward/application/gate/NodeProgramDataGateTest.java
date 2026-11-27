package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.GateFrontends;
import dev.ward.infrastructure.analysis.node.NodeDependencyAdapters;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.DeclarativeRuleMatcher;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Constants exported by another file are program data, and a change to them is seen. */
class NodeProgramDataGateTest {
  private static final String RULE = "ward:typescript/dom-xss";
  private static final String STEPS =
      "export const Steps = { hints: [ { text: 'one' }, { text: `two ${1 + 1}` } ] }\n";
  private static final String MAIN =
      "import md from 'snarkdown'\n"
          + "import { Steps } from './steps'\n"
          + "function show(hint) { document.body.innerHTML = md(hint.text) }\n"
          + "export function run() { for (const hint of Steps.hints) { show(hint) } }\n";

  @TempDir Path root;

  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());

  private List<AnalysisFinding> run(final SourceUnit... units) {
    final List<SourceUnit> all = List.of(units);
    final List<RuleDefinition> rules =
        new ClasspathRuleSource()
            .load().rules().stream().filter(candidate -> RULE.equals(candidate.id())).toList();
    return new UnitResultCache(
        new RuleEngine(new DeclarativeRuleMatcher()),
        AnalysisCacheFixtures.codec(),
        "test",
        new NodeDependencyAdapters().forProject(root, all)
    )
        .run(AnalysisCacheFixtures.NO_CACHE, all, frontends, rules)
        .findings();
  }

  private static SourceUnit typescript(final String path, final String text) {
    return new SourceUnit(path, AnalysisLanguage.TYPESCRIPT, text);
  }

  @Test
  void aConstantExportedByAnotherFileReachesAPrivateFunctionAsProgramData() {
    assertThat(run(typescript("src/steps.ts", STEPS), typescript("src/main.ts", MAIN))).isEmpty();
  }

  @Test
  void aTextThatTheOtherFileDoesNotWriteAsAConstantIsReported() {
    final String steps = STEPS.replace("'one'", "load()");
    assertThat(run(typescript("src/steps.ts", steps), typescript("src/main.ts", MAIN))).hasSize(1);
  }

  @Test
  void aChangeInTheOtherFileWithRequestDataIsReported() {
    final String steps = STEPS + "Steps.hints[0].text = req.query.q\n";
    assertThat(run(typescript("src/steps.ts", steps), typescript("src/main.ts", MAIN))).hasSize(1);
  }

  @Test
  void aChangeInTheFileOfTheSinkIsReported() {
    final String main = MAIN + "Steps.hints.push({ text: req.query.q })\n";
    assertThat(run(typescript("src/steps.ts", STEPS), typescript("src/main.ts", main))).hasSize(1);
  }
}
