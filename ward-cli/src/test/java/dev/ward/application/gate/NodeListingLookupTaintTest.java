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

/**
 * A key that a cached listing of a directory proved to start the name of an entry is not a path:
 * the function in another file lists an entry only for a key that starts its name, and the caller
 * left or took the other branch on an empty list.
 */
class NodeListingLookupTaintTest {
  private static final String PATH_RULE = "ward:typescript/path-traversal";
  private static final String READER =
      "const cache: Record<string, { fixes: string[] }> = {}\n"
          + "\n"
          + "export const readFixes = (key: string) => {\n"
          + "  if (cache[key]) {\n"
          + "    return cache[key]\n"
          + "  }\n"
          + "  const files = fs.readdirSync('data/fixes')\n"
          + "  const fixes: string[] = []\n"
          + "  for (const file of files) {\n"
          + "    if (file.startsWith(`${key}_`)) {\n"
          + "      fixes.push(file)\n"
          + "    }\n"
          + "  }\n"
          + "  cache[key] = { fixes }\n"
          + "  return cache[key]\n"
          + "}\n";
  private static final String ROUTE =
      "import { readFixes } from '../lib/fixes'\n"
          + "\n"
          + "export const check = (req: any, res: any) => {\n"
          + "  const key = req.body.key\n"
          + "  const fixData = readFixes(key)\n"
          + "  if (fixData.fixes.length === 0) {\n"
          + "    res.status(404).json({})\n"
          + "  } else {\n"
          + "    return fs.readFileSync('./data/' + key + '.info.yml')\n"
          + "  }\n"
          + "}\n";

  @TempDir Path root;

  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());

  private List<AnalysisFinding> run(final String reader, final String route) {
    final List<SourceUnit> units =
        List.of(
            new SourceUnit(
                "lib/fixes.ts",
                AnalysisLanguage.TYPESCRIPT,
                "import fs from 'node:fs'\n" + reader
            ),
            new SourceUnit(
                "routes/fixes.ts",
                AnalysisLanguage.TYPESCRIPT,
                "import fs from 'node:fs'\n" + route
            )
        );
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

  @Test
  void aKeyThatACachedListingInAnotherFileProvedToStartAnEntryName() {
    assertThat(run(READER, ROUTE)).isEmpty();
  }

  @Test
  void aListingWithoutThePrefixTestStillReports() {
    assertThat(run(READER.replace("file.startsWith(`${key}_`)", "true"), ROUTE)).hasSize(1);
  }

  @Test
  void aCacheThatAnotherPlaceWritesToStillReports() {
    final String leak =
        READER + "export function leak (req: any) {\n  cache[req.body.id] = { fixes: ['x'] }\n}\n";
    assertThat(run(leak, ROUTE)).hasSize(1);
  }

  @Test
  void aCallerWhoseEmptyBranchIsTheOneThatReadsStillReports() {
    assertThat(run(READER, ROUTE.replace("res.status(404).json({})", "fs.readFileSync(key)")))
        .hasSize(1);
  }
}
