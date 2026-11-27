package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RelatedFile;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.NodeUnsafeValueCheck;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** The two files and the check that the program-data tests run. */
final class NodeProgramDataFixtures {
  static final Pattern MARKUP = Pattern.compile("\\.innerHTML=");
  static final String HINTS =
      "export const Steps = { name: 'a', hints: [ { text: 'one', go: () => 1 },"
          + " { text: `two ${1 + 1}`, go: wait(5) } ] }\n";
  static final String MAIN =
      "import md from 'snarkdown'\nimport { Steps } from './steps'\n"
          + "const all = [Steps]\n"
          + "function show(hint) { el.innerHTML = md(hint.text) }\n"
          + "function run(n) { const s = all.find((x) => x.name === n) ?? Steps;"
          + " for (const hint of s.hints) { show(hint); hint.go() } }\n";

  private NodeProgramDataFixtures() {}

  static Map<String, String> steps() {
    return Map.of("src/steps.ts", HINTS);
  }

  static List<RuleMatch> markup(final String source, final Map<String, String> others) {
    final SourceUnit unit = unit("src/main.ts", source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    final List<RelatedFile> related = new ArrayList<>();
    others.forEach(
        (path, text) -> {
          final SourceUnit other = unit(path, text);
          related.add(new RelatedFile(other, new AntlrLanguageFrontend().parse(other).tree()));
        }
    );
    final FlowView flow =
        new FlowView(new NodeControlFlow().lower(unit, parsed.tree()).graphs(), List.of(), related);
    return new NodeUnsafeValueCheck(MARKUP, false, NodeSafeValues.Mode.MARKUP, "m")
        .matches(parsed.tree(), flow, unit);
  }

  private static SourceUnit unit(final String path, final String text) {
    return new SourceUnit(path, AnalysisLanguage.TYPESCRIPT, text);
  }
}
