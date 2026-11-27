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
 * A key that a lookup in a collection of the program accepted, and a null check proved, is not a
 * path: the callee gives a value only for a member, the collection is written with program data
 * only, and the caller exits on null.
 */
class NodeMemberLookupTaintTest {
  private static final String PATH_RULE = "ward:typescript/path-traversal";
  private static final String REGISTRY =
      "let registry: Map<string, string> | null = null\n"
          + "\n"
          + "export async function loadRegistry (): Promise<Map<string, string>> {\n"
          + "  if (registry === null) {\n"
          + "    registry = new Map<string, string>()\n"
          + "    for (const name of ['a', 'b']) {\n"
          + "      registry.set(name, 'x')\n"
          + "    }\n"
          + "  }\n"
          + "  return registry\n"
          + "}\n";
  private static final String HEAD =
      "import fs from 'node:fs/promises'\n"
          + "import { loadRegistry } from '../lib/registry'\n"
          + "\n";
  private static final String LOOKUP =
      "export const lookup = async (key: string) => {\n"
          + "  const known = await loadRegistry()\n"
          + "  if (known.has(key)) {\n"
          + "    return known.get(key) ?? null\n"
          + "  }\n"
          + "  return null\n"
          + "}\n\n";
  private static final String EXITING =
      "export const check = async (req: any, res: any) => {\n"
          + "  const key = req.body.key\n"
          + "  let found\n"
          + "  try {\n"
          + "    found = await lookup(key)\n"
          + "    if (found == null) {\n"
          + "      res.status(404).json({})\n"
          + "      return\n"
          + "    }\n"
          + "  } catch (error) {\n"
          + "    return\n"
          + "  }\n"
          + "  return await fs.readFile('./data/' + key + '.yml')\n"
          + "}\n";

  @TempDir Path root;

  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());

  private List<AnalysisFinding> run(final String registry, final String route) {
    return run(
        List.of(
            new SourceUnit("lib/registry.ts", AnalysisLanguage.TYPESCRIPT, registry),
            new SourceUnit("routes/snippet.ts", AnalysisLanguage.TYPESCRIPT, route)
        )
    );
  }

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

  @Test
  void aKeyThatWasLookedUpInACollectionOfTheProgramAndChecked() {
    assertThat(run(REGISTRY, HEAD + LOOKUP + EXITING)).isEmpty();
  }

  @Test
  void aCheckWhoseNullBranchDoesNotExitStillReports() {
    final String falls = EXITING.replace("      return\n    }\n  } catch", "    }\n  } catch");
    assertThat(run(REGISTRY, HEAD + LOOKUP + falls)).hasSize(1);
  }

  @Test
  void aCollectionThatARequestWritesToInAnotherFileStillReports() {
    final String other =
        "import { loadRegistry } from '../lib/registry'\n"
            + "export const add = async (req: any) => {\n"
            + "  const all = await loadRegistry()\n"
            + "  all.set(req.body.id, 'x')\n"
            + "}\n";
    final List<SourceUnit> units =
        List.of(
            new SourceUnit("lib/registry.ts", AnalysisLanguage.TYPESCRIPT, REGISTRY),
            new SourceUnit(
                "routes/snippet.ts",
                AnalysisLanguage.TYPESCRIPT,
                HEAD + LOOKUP + EXITING
            ),
            new SourceUnit("routes/other.ts", AnalysisLanguage.TYPESCRIPT, other)
        );
    assertThat(run(units)).extracting(AnalysisFinding::file).containsOnly("routes/snippet.ts");
  }

  @Test
  void aKeyWrittenWithARequestValueInTheCollectionFileStillReports() {
    final String writes =
        REGISTRY + "export function remember (req: any) {\n  registry?.set(req.body.id, 'x')\n}\n";
    assertThat(run(writes, HEAD + LOOKUP + EXITING)).hasSize(1);
  }
}
