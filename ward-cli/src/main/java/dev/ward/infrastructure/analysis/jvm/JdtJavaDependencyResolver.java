package dev.ward.infrastructure.analysis.jvm;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.jvm.JavaDependencyResolver;
import dev.ward.analysis.domain.jvm.JavaSourceRoot;
import dev.ward.analysis.domain.port.DependencyResolver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.VariableDeclaration;

/**
 * Resolves Java edges with bindings: the names of the source edges, plus the project types that
 * bindings find on expressions and variables, such as the type of a {@code var} or of a call. The
 * classpath gives the library types the bindings need. The union can only add edges to the source
 * edges.
 */
public final class JdtJavaDependencyResolver implements DependencyResolver {
  private static final Logger LOGGER = Logger.getLogger(JdtJavaDependencyResolver.class.getName());
  private static final String BINDING = "binding:";
  private static final String COMPLIANCE = "25";

  private final Path projectRoot;
  private final List<String> classpath;
  private final List<JavaSourceRoot> roots;

  /**
   * Creates the resolver for the project at {@code projectRoot}.
   *
   * @param projectRoot the project root, which the summaries' paths are relative to
   * @param classpath the classpath entries, absolute or relative to the project root
   * @param roots the source roots of the project, which the bindings resolve source types from
   */
  public JdtJavaDependencyResolver(
      final Path projectRoot, final List<String> classpath, final List<JavaSourceRoot> roots) {
    this.projectRoot = projectRoot;
    this.classpath = List.copyOf(classpath);
    this.roots = List.copyOf(roots);
  }

  /** Returns the classpath entries of a file: separated by the path separator or newlines. */
  public static List<String> classpathEntries(final Path file) {
    final String text;
    try {
      text = Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read classpath file " + file, e);
    }
    final List<String> entries = new ArrayList<>();
    for (final String line : text.split("\\R")) {
      for (final String entry :
          line.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
        if (!entry.isBlank()) {
          entries.add(entry.trim());
        }
      }
    }
    return entries;
  }

  @Override
  public List<DependencyEdge> resolve(final List<FileSummary> summaries) {
    final List<DependencyEdge> named = new JavaDependencyResolver(roots).resolve(summaries);
    final Map<String, String> declaringFile = declaringFiles(summaries);
    final List<DependencyEdge> inferred = bindingEdges(summaries, declaringFile);
    final Map<List<String>, DependencyEdge> union = new LinkedHashMap<>();
    for (final DependencyEdge edge : named) {
      union.putIfAbsent(List.of(edge.from(), edge.to()), edge);
    }
    for (final DependencyEdge edge : inferred) {
      union.putIfAbsent(List.of(edge.from(), edge.to()), edge);
    }
    LOGGER.fine(
        () ->
            "Java bindings: "
                + summaries.size()
                + " files, "
                + inferred.size()
                + " binding edges, "
                + union.size()
                + " edges in total");
    return new ArrayList<>(union.values());
  }

  /** Maps every declared type, nested types included, to the file that declares it. */
  private static Map<String, String> declaringFiles(final List<FileSummary> summaries) {
    final Map<String, String> files = new HashMap<>();
    for (final FileSummary summary : summaries) {
      for (final Symbol type : summary.declared()) {
        files.put(type.name(), summary.path());
      }
    }
    return files;
  }

  private List<DependencyEdge> bindingEdges(
      final List<FileSummary> summaries, final Map<String, String> declaringFile) {
    final String[] paths =
        summaries.stream().map(FileSummary::path).map(this::absolute).toArray(String[]::new);
    final List<DependencyEdge> edges = new ArrayList<>();
    parser()
        .createASTs(
            paths,
            null,
            new String[0],
            new BindingCollector(projectRoot, declaringFile, edges),
            null);
    return edges;
  }

  private String absolute(final String path) {
    return projectRoot.resolve(path).toAbsolutePath().normalize().toString();
  }

  /** Creates a batch parser with bindings, the classpath and the project's source roots. */
  private ASTParser parser() {
    final ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
    final Map<String, String> options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(COMPLIANCE, options);
    parser.setCompilerOptions(options);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setResolveBindings(true);
    parser.setBindingsRecovery(true);
    parser.setEnvironment(classpathEntriesAbsolute(), sourceRoots(), null, true);
    return parser;
  }

  private String[] classpathEntriesAbsolute() {
    return classpath.stream()
        .map(entry -> projectRoot.resolve(entry).toAbsolutePath().normalize().toString())
        .toArray(String[]::new);
  }

  private String[] sourceRoots() {
    return roots.stream()
        .map(root -> projectRoot.resolve(root.directory()).toAbsolutePath().normalize())
        .filter(Files::isDirectory)
        .map(Path::toString)
        .toArray(String[]::new);
  }

  /** Collects the project types that the bindings of each compilation unit find. */
  private static final class BindingCollector extends FileASTRequestor {
    private final Path projectRoot;
    private final Map<String, String> declaringFile;
    private final List<DependencyEdge> edges;

    BindingCollector(
        final Path projectRoot,
        final Map<String, String> declaringFile,
        final List<DependencyEdge> edges) {
      super();
      this.projectRoot = projectRoot;
      this.declaringFile = declaringFile;
      this.edges = edges;
    }

    @Override
    public void acceptAST(final String sourceFilePath, final CompilationUnit ast) {
      final String from =
          projectRoot.relativize(Path.of(sourceFilePath)).toString().replace('\\', '/');
      ast.accept(
          new ASTVisitor(true) {
            @Override
            public boolean preVisit2(final ASTNode node) {
              if (node instanceof Expression expression) {
                addType(from, ast, node, expression.resolveTypeBinding());
              } else if (node instanceof VariableDeclaration declaration) {
                addType(
                    from,
                    ast,
                    node,
                    declaration.resolveBinding() == null
                        ? null
                        : declaration.resolveBinding().getType());
              }
              return true;
            }
          });
    }

    private void addType(
        final String from,
        final CompilationUnit ast,
        final ASTNode node,
        final ITypeBinding binding) {
      final Optional<String> qualified = projectType(binding);
      if (qualified.isEmpty()) {
        return;
      }
      final String to = declaringFile.get(qualified.get());
      if (to != null && !to.equals(from)) {
        edges.add(
            new DependencyEdge(
                from, to, new Reference(BINDING + qualified.get(), span(ast, node))));
      }
    }

    /** The qualified name of the erasure of a non-array, non-primitive binding. */
    private static Optional<String> projectType(final ITypeBinding binding) {
      if (binding == null) {
        return Optional.empty();
      }
      final ITypeBinding type = binding.isArray() ? binding.getElementType() : binding;
      if (type == null || type.isPrimitive() || type.isNullType()) {
        return Optional.empty();
      }
      final ITypeBinding erasure = type.getErasure();
      return erasure == null || erasure.getQualifiedName().isEmpty()
          ? Optional.empty()
          : Optional.of(erasure.getQualifiedName());
    }

    private static Span span(final CompilationUnit ast, final ASTNode node) {
      final int start = node.getStartPosition();
      final int end = start + node.getLength();
      final int line = Math.max(1, ast.getLineNumber(start));
      final int endLine = Math.max(line, ast.getLineNumber(Math.max(start, end - 1)));
      final int column = Math.max(1, ast.getColumnNumber(start) + 1);
      final int endColumn = Math.max(1, ast.getColumnNumber(Math.max(start, end - 1)) + 1);
      return new Span(line, column, endLine, endColumn, start, Math.max(start, end));
    }
  }
}
