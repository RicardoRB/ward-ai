package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.AnalysisLanguage;

/** The scenario of {@link NodeMemberLookupTest} and {@link NodeMemberLookupNearTest}. */
final class NodeMemberLookups {
  static final String REGISTRY =
      "let registry: Map<string, string> | null = null;\n"
          + "async function load() {\n"
          + "  if (registry === null) {\n"
          + "    registry = new Map<string, string>();\n"
          + "    for (const name of ['a', 'b']) {\n"
          + "      registry.set(name, 'x');\n"
          + "    }\n"
          + "  }\n"
          + "  return registry;\n"
          + "}\n";
  static final String LOOKUP =
      "const lookup = async (key) => {\n"
          + "  const known = await load();\n"
          + "  if (known.has(key)) {\n"
          + "    return known.get(key) ?? null;\n"
          + "  }\n"
          + "  return null;\n"
          + "};\n";
  static final String CHECK =
      "async function check(req) {\n"
          + "  const key = req.query;\n"
          + "  let found;\n"
          + "  try {\n"
          + "    found = await lookup(key);\n"
          + "    if (found == null) {\n"
          + "      return;\n"
          + "    }\n"
          + "  } catch (error) {\n"
          + "    return;\n"
          + "  }\n"
          + "  sink(key);\n"
          + "}\n";

  private NodeMemberLookups() {}

  static int found(final String registry, final String lookup, final String check) {
    return NodeTaintFixtures.runFile(AnalysisLanguage.TYPESCRIPT, registry + lookup + check).size();
  }

  static int found(final String extra) {
    return found(REGISTRY + extra, LOOKUP, CHECK);
  }
}
