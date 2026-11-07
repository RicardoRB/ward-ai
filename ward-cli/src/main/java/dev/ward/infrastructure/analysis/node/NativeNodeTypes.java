package dev.ward.infrastructure.analysis.node;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.node.NodeModuleResolver;
import dev.ward.analysis.domain.node.NodeProjectLayout;
import dev.ward.analysis.domain.node.NodeScopeAnalysis;
import dev.ward.analysis.domain.node.types.NativeNodeTypeFacts;
import dev.ward.analysis.domain.node.types.Program;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.types.CachedTypeFacts;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.ClasspathBuiltinNames;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The type facts of a project's TypeScript and JavaScript units, read natively from its sources,
 * the {@code .d.ts} files of its packages and the library of the installed {@code typescript}
 * package. It starts no process and needs no Node: without the packages, their types are unknown.
 * The project is read when the first unit asks for facts.
 */
public final class NativeNodeTypes {
  private static final Logger LOGGER = Logger.getLogger(NativeNodeTypes.class.getName());
  private static final String CACHE_DIRECTORY = ".ward/cache/types/node";
  private static final Set<String> SKIPPED_DIRECTORIES =
      Set.of(
          "node_modules", ".git", ".ward", "dist", "build", "out", "coverage", "target", ".next");
  private static final List<String> EXTENSIONS =
      List.of(".ts", ".tsx", ".mts", ".cts", ".js", ".jsx", ".mjs", ".cjs");

  private final Path projectRoot;
  private final ReentrantLock lock = new ReentrantLock();
  private TypeFacts facts;

  /** Creates the facts of the project at {@code projectRoot}. */
  public NativeNodeTypes(final Path projectRoot) {
    this.projectRoot = projectRoot.toAbsolutePath().normalize();
  }

  /** Returns the type facts; the project is opened on the first query. */
  public TypeFacts facts() {
    return new TypeFacts() {
      @Override
      public UnitTypeFacts factsOf(
          final SourceUnit unit, final List<Span> spans, final Set<FactKind> kinds) {
        lock.lock();
        try {
          if (facts == null) {
            facts = open();
          }
          return facts.factsOf(unit, spans, kinds);
        } finally {
          lock.unlock();
        }
      }
    };
  }

  private TypeFacts open() {
    final Set<String> files = projectFiles();
    final NodeProjectLayout layout = NodeLayouts.read(projectRoot, files);
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    final Program program =
        new Program(
            new ProjectDeclarationFiles(projectRoot),
            new DiskDeclarationCache(projectRoot.resolve(CACHE_DIRECTORY)),
            frontend,
            files,
            new NodeModuleResolver(layout)::resolve);
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine("event=native-node-types-opened files=" + files.size());
    }
    return new CachedTypeFacts(
        new NativeNodeTypeFacts(
            program, frontend, new NodeScopeAnalysis(ClasspathBuiltinNames.typescript())),
        "");
  }

  private Set<String> projectFiles() {
    final Set<String> found = new HashSet<>();
    try {
      Files.walkFileTree(
          projectRoot,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                final Path dir, final BasicFileAttributes attrs) {
              final String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
              final boolean skipped =
                  !dir.equals(projectRoot)
                      && (SKIPPED_DIRECTORIES.contains(name) || name.startsWith("."));
              return skipped ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
              final String name = file.getFileName().toString();
              if (attrs.isRegularFile() && EXTENSIONS.stream().anyMatch(name::endsWith)) {
                found.add(projectRoot.relativize(file).toString().replace('\\', '/'));
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException unreadable) {
      LOGGER.log(Level.WARNING, "event=native-node-types-walk-failed");
    }
    return found;
  }
}
