package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.AnalysisLanguage;

/** The scenario of {@link NodeListingLookupTest} and {@link NodeListingLookupNearTest}. */
final class NodeListingLookups {
  static final String READ =
      "const cache: Record<string, Fix> = {};\n"
          + "export const readFixes = (key: string) => {\n"
          + "  if (cache[key]) {\n"
          + "    return cache[key];\n"
          + "  }\n"
          + "  const files = fs.readdirSync(DIR);\n"
          + "  const fixes: string[] = [];\n"
          + "  for (const file of files) {\n"
          + "    if (file.startsWith(`${key}_`)) {\n"
          + "      fixes.push(file);\n"
          + "    }\n"
          + "  }\n"
          + "  cache[key] = { fixes, correct };\n"
          + "  return cache[key];\n"
          + "};\n";
  static final String EXITING =
      "function serve(req) {\n"
          + "  const key = req.query;\n"
          + "  const data = readFixes(key);\n"
          + "  if (data.fixes.length === 0) {\n"
          + "    return;\n"
          + "  }\n"
          + "  sink(key);\n"
          + "}\n";
  static final String BRANCHING =
      "function check(req) {\n"
          + "  const key = req.query;\n"
          + "  const data = readFixes(key);\n"
          + "  if (data.fixes.length === 0) {\n"
          + "    respond(404);\n"
          + "  } else {\n"
          + "    sink(key);\n"
          + "  }\n"
          + "}\n";

  private NodeListingLookups() {}

  static int found(final String read, final String caller) {
    return NodeTaintFixtures.runFile(AnalysisLanguage.TYPESCRIPT, read + caller).size();
  }
}
