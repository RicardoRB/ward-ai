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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A call of an exported function of another file is followed through the import that names it. */
class NodeCrossModuleTaintTest {
  private static final String BRACE = "}\n";
  private static final String CLOSE = "\n" + BRACE;
  private static final int LAST_TAINTED = 3;
  private static final String PATH_RULE = "ward:typescript/path-traversal";
  private static final String HEADER =
      "import fs from 'node:fs'\n"
          + "import { type Request } from 'express'\n"
          + "import { fixed, wanted } from '../lib/files'\n"
          + "import * as files from '../lib/files'\n"
          + "\n";
  private static final String FILES =
      "export function fixed (name: string) {\n"
          + "  return 'static.txt'\n"
          + BRACE
          + "\n"
          + "export function wanted (req: any) {\n"
          + "  return req.query.f\n"
          + BRACE
          + "\n"
          + "export function passthrough (name: string) {\n"
          + "  return name\n"
          + BRACE;
  private static final int HEADER_LINES = 2;
  private static final int HANDLER_LINES = 3;
  private static final int NAMED_TAINTED_LINE = 11;
  private static final int NAMESPACE_TAINTED_LINE = 15;
  private static final int PASSTHROUGH_LINE = 23;

  @TempDir Path root;

  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());

  private List<AnalysisFinding> run(final List<SourceUnit> units) {
    final List<RuleDefinition> rules =
        new ClasspathRuleSource()
            .load().rules().stream().filter(candidate -> PATH_RULE.equals(candidate.id())).toList();
    return new UnitResultCache(
        new RuleEngine(new DeclarativeRuleMatcher()),
        AnalysisCacheFixtures.codec(),
        "test",
        new NodeDependencyAdapters().forProject(root, units)
    )
        .run(AnalysisCacheFixtures.NO_CACHE, units, frontends, rules)
        .findings();
  }

  private static SourceUnit typescript(final String path, final String text) {
    return new SourceUnit(path, AnalysisLanguage.TYPESCRIPT, text);
  }

  @Test
  void aConstantBuiltInAnotherFileIsNotAPathButASourceThereStillIs() {
    final String route =
        HEADER
            + "export function named (req: Request) {\n"
            + "  return fs.readFileSync(fixed(req.query.n as string))\n"
            + BRACE
            + "\n"
            + "export function tainted (req: Request) {\n"
            + "  return fs.readFileSync(wanted(req))\n"
            + BRACE
            + "\n"
            + "export function viaNamespace (req: Request) {\n"
            + "  return fs.readFileSync(files.wanted(req))\n"
            + BRACE
            + "\n"
            + "export function constantViaNamespace (req: Request) {\n"
            + "  return fs.readFileSync(files.fixed(req.query.n as string))\n"
            + BRACE
            + "\n"
            + "export function passes (req: Request) {\n"
            + "  return fs.readFileSync(files.passthrough(req.query.n as string))\n"
            + BRACE;

    final List<AnalysisFinding> found =
        run(List.of(typescript("lib/files.ts", FILES), typescript("routes/a.ts", route)));

    assertThat(found).extracting(AnalysisFinding::file).containsOnly("routes/a.ts");
    assertThat(found)
        .extracting(AnalysisFinding::line)
        .containsExactlyInAnyOrder(NAMED_TAINTED_LINE, NAMESPACE_TAINTED_LINE, PASSTHROUGH_LINE);
  }
  @Test
  void aHelperThatReturnsAnOperandOfAConditionalStaysTaintedButOneThatTestsOnlyIsClean() {
    final List<String> bodies =
        List.of(
            "return str ? str : ''",
            "return str.length > 1 ? str.substr(0, 1) : str",
            "return str.length > 1 ? str.substr(0, 1) + '...' : 'x'",
            "str = str.replace(/x/g, '')\n  return str.length > 1 ? str : ''",
            "return str.length > 1 ? 'long' : 'short'",
            "return str === 'a' ? 'first' : 'other'"
        );
    final String helpers =
        IntStream.range(0, bodies.size())
            .mapToObj(
                i -> "export const h" + i + " = function (str: string) {\n  " + bodies.get(
                    i
                ) + CLOSE
            )
            .collect(Collectors.joining());
    final String imports =
        IntStream.range(0, bodies.size())
            .mapToObj(i -> "import { h" + i + " } from '../lib/helpers'\n")
            .collect(Collectors.joining());
    final String handlers =
        IntStream.range(0, bodies.size())
            .mapToObj(
                i -> "export function r" + i + " (req: Request) {\n"
                    + "  return fs.readFileSync(h" + i + "(req.query.n as string))" + CLOSE
            )
            .collect(Collectors.joining());
    final String route =
        "import fs from 'node:fs'\nimport { type Request } from 'express'\n" + imports + handlers;
    final int first = HEADER_LINES + bodies.size() + 2;

    final List<AnalysisFinding> found =
        run(List.of(typescript("lib/helpers.ts", helpers), typescript("routes/b.ts", route)));

    assertThat(found)
        .extracting(finding -> (finding.line() - first) / HANDLER_LINES)
        .containsExactlyInAnyOrder(0, 1, 2, LAST_TAINTED);
  }
}
