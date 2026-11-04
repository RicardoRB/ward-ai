package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class NodeFileSummarizerTest {

  @Test
  void recordsEveryStaticImportForm() {
    final FileSummary summary =
        summarize(
            AnalysisLanguage.TYPESCRIPT,
            """
            import { a as b, c } from "./x.js";
            import "./side";
            import * as ns from "../n";
            import d from "./default";
            """);

    assertThat(references(summary))
        .containsExactly("static:./x.js", "static:./side", "static:../n", "static:./default");
  }

  @Test
  void recordsTypeOnlyImportsWithTypePrefix() {
    final FileSummary summary =
        summarize(
            AnalysisLanguage.TYPESCRIPT,
            """
            import type { Order } from "./order";
            import type * as all from "./all";
            import type from "./named-type";
            """);

    assertThat(references(summary))
        .containsExactly("type:./order", "type:./all", "static:./named-type");
  }

  @Test
  void recordsRequireDynamicImportAndExportFromWithLiteralArgumentsOnly() {
    final FileSummary summary =
        summarize(
            AnalysisLanguage.JAVASCRIPT,
            """
            const r = require("./r");
            const dyn = import("./dyn");
            const t = import(`./t/${name}`);
            const q = require(name);
            export * from "./all";
            export { e as f } from "./e";
            // require("./commented")
            """);

    assertThat(references(summary))
        .containsExactly("require:./r", "dynamic:./dyn", "static:./all", "static:./e");
  }

  @Test
  void recordsImportEqualsRequireAsRequire() {
    final FileSummary summary =
        summarize(AnalysisLanguage.TYPESCRIPT, "import legacy = require(\"./legacy\");\n");

    assertThat(references(summary)).containsExactly("require:./legacy");
  }

  @Test
  void recordsExportedNamesOfDeclarationsListsAndStarReExports() {
    final FileSummary summary =
        summarize(
            AnalysisLanguage.TYPESCRIPT,
            """
            export const g = 1;
            export async function run() {}
            export class Repo {}
            export type Port = Row;
            export { x as y, z };
            export * from "./all";
            export default function fn() {}
            """);

    assertThat(exportNames(summary))
        .containsExactly("g", "run", "Repo", "Port", "y", "z", "*:./all", "default");
  }

  @Test
  void recordsCommonJsNamedExports() {
    final FileSummary summary =
        summarize(
            AnalysisLanguage.JAVASCRIPT, "module.exports.make = () => 1;\nexports.run = 2;\n");

    assertThat(exportNames(summary)).containsExactly("make", "run");
  }

  private static FileSummary summarize(final AnalysisLanguage language, final String text) {
    final SourceUnit unit = new SourceUnit("src/a.ts", language, text);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    assertThat(parsed.diagnostics()).isEmpty();
    return new NodeFileSummarizer().summarize(unit, parsed.tree());
  }

  private static List<String> references(final FileSummary summary) {
    return summary.imports().stream().map(Reference::specifier).toList();
  }

  private static List<String> exportNames(final FileSummary summary) {
    return summary.exports().stream().map(Symbol::name).toList();
  }
}
