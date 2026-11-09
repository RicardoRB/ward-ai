package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.node.NodeControlFlow;
import dev.ward.analysis.domain.node.NodeScopeAnalysis;
import dev.ward.analysis.domain.port.BuiltinNames;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.scope.CachedScopes;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The frontends the fixture run of this module gives the rule engine: the Node parser, control flow
 * and scopes for TypeScript and JavaScript, with the built-in names read from this module's
 * resources.
 */
final class NodeFixtureFrontends {

  private static final String BUILTINS = "/dev/ward/analysis/lang/node/builtins/";
  private static final String TYPESCRIPT_ENVIRONMENT = "typescript";
  private static final String ES = "es2024";
  private static final String BROWSER = "browser";
  private static final String NODE = "node";

  private NodeFixtureFrontends() {}

  /** The support of TypeScript and JavaScript, with every capability a rule may ask for. */
  static List<FrontendSupport> create() {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    final Set<RuleCapability> flow =
        Set.of(
            RuleCapability.SYNTAX,
            RuleCapability.SYMBOLS,
            RuleCapability.CFG,
            RuleCapability.DATAFLOW,
            RuleCapability.SCOPES);
    return List.of(
        new FrontendSupport(
            AnalysisLanguage.TYPESCRIPT,
            frontend,
            flow,
            new NodeControlFlow(),
            new CachedScopes(
                new NodeScopeAnalysis(names(ES, TYPESCRIPT_ENVIRONMENT, BROWSER, NODE)))),
        new FrontendSupport(
            AnalysisLanguage.JAVASCRIPT,
            frontend,
            flow,
            new NodeControlFlow(),
            new CachedScopes(new NodeScopeAnalysis(names(ES, BROWSER, NODE)))));
  }

  /** The names of the given environment files, one name per line; the same set for any request. */
  private static BuiltinNames names(final String... environments) {
    final Set<String> names = new HashSet<>();
    for (final String environment : environments) {
      names.addAll(read(environment));
    }
    final Set<String> all = Set.copyOf(names);
    return requested -> all;
  }

  private static Set<String> read(final String environment) {
    try (InputStream stream =
        NodeFixtureFrontends.class.getResourceAsStream(BUILTINS + environment + ".txt")) {
      return Set.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R"));
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
