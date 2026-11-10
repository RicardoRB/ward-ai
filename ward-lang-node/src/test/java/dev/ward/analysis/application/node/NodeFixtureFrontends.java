package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.TypeFactsFixture;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.node.NodeControlFlow;
import dev.ward.analysis.domain.node.NodeModuleResolver;
import dev.ward.analysis.domain.node.NodeProjectLayout;
import dev.ward.analysis.domain.node.NodeScopeAnalysis;
import dev.ward.analysis.domain.node.types.DeclarationCache;
import dev.ward.analysis.domain.node.types.DeclarationFiles;
import dev.ward.analysis.domain.node.types.NativeNodeTypeFacts;
import dev.ward.analysis.domain.node.types.Program;
import dev.ward.analysis.domain.port.BuiltinNames;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.scope.CachedScopes;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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

  /** The frontends of a fixture folder: its types.json, when it has one, gives the type facts. */
  static List<FrontendSupport> forFolder(final Path directory) {
    final Path typesJson = directory.resolve("types.json");
    return create(Files.isRegularFile(typesJson) ? Optional.of(typesJson) : Optional.empty());
  }

  /**
   * The support of TypeScript and JavaScript, with every capability a rule may ask for. The type
   * facts of a fixture folder come from its {@code types.json} when it has one, as in ward-cli;
   * otherwise they come from the native TypeScript resolver, which reads the fixture itself.
   */
  static List<FrontendSupport> create(final Optional<Path> typesJson) {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    final Optional<TypeFacts> types =
        Optional.of(
            typesJson
                .map(NodeFixtureFrontends::fixtureFacts)
                .orElseGet(NodeFixtureFrontends::nativeFacts));
    final Set<RuleCapability> flow =
        Set.of(
            RuleCapability.SYNTAX,
            RuleCapability.SYMBOLS,
            RuleCapability.CFG,
            RuleCapability.DATAFLOW,
            RuleCapability.SCOPES,
            RuleCapability.TYPES);
    return List.of(
        new FrontendSupport(
            AnalysisLanguage.TYPESCRIPT,
            frontend,
            flow,
            new NodeControlFlow(),
            new CachedScopes(
                new NodeScopeAnalysis(names(ES, TYPESCRIPT_ENVIRONMENT, BROWSER, NODE))),
            types,
            Set.of(),
            Set.of()),
        new FrontendSupport(
            AnalysisLanguage.JAVASCRIPT,
            frontend,
            flow,
            new NodeControlFlow(),
            new CachedScopes(new NodeScopeAnalysis(names(ES, BROWSER, NODE))),
            types,
            Set.of(),
            Set.of()));
  }

  /** The facts of a fixture folder's types.json, which answer for every unit as in ward-cli. */
  private static TypeFacts fixtureFacts(final Path typesJson) {
    final dev.ward.analysis.domain.types.UnitTypeFacts facts = TypeFactsFixture.read(typesJson);
    return (unit, spans, kinds) -> facts;
  }

  /** The native TypeScript resolver with no project: a fixture's own declarations answer. */
  private static TypeFacts nativeFacts() {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    final NodeProjectLayout layout = new NodeProjectLayout(Set.of(), List.of(), List.of());
    final Program program =
        new Program(
            NO_DECLARATIONS,
            DeclarationCache.NONE,
            frontend,
            layout.files(),
            new NodeModuleResolver(layout)::resolve);
    return new NativeNodeTypeFacts(
        program, frontend, new NodeScopeAnalysis(names(ES, TYPESCRIPT_ENVIRONMENT, BROWSER, NODE)));
  }

  /** No declaration files: the native resolver knows only what the fixture declares itself. */
  private static final DeclarationFiles NO_DECLARATIONS =
      new DeclarationFiles() {
        @Override
        public Optional<String> read(final String path) {
          return Optional.empty();
        }

        @Override
        public boolean exists(final String path) {
          return false;
        }

        @Override
        public List<String> list(final String directory) {
          return List.of();
        }

        @Override
        public Optional<String> packageHash(final String directory) {
          return Optional.empty();
        }
      };

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
