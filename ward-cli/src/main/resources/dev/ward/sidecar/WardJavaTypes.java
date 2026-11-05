/*
 * Ward type sidecar for Java. Answers the static type of expression spans with javac's Trees API.
 *
 * Run by the project's java in source-launch mode:
 *   java WardJavaTypes.java <projectRoot> <sourceRoot>...
 *
 * Protocol, one JSON object per line: a request {"path","text","spans":[[start,end],...]} gets the
 * response {"types":{"start:end":"fully.qualified.Type"}}. Spans with no answer are left out. A
 * malformed request ends the process with status 2. Diagnostics go to stderr only, never the source.
 * Libraries are not on the class path, so their types come back as error types and are omitted.
 */

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

public final class WardJavaTypes {

  private static final int MALFORMED_REQUEST = 2;
  private static final int NO_COMPILER = 1;

  private WardJavaTypes() {}

  public static void main(final String[] args) throws IOException {
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      System.err.println("no system Java compiler: run the sidecar on a JDK");
      System.exit(NO_COMPILER);
    }
    final List<Path> sourceRoots = new ArrayList<>();
    for (int index = 1; index < args.length; index++) {
      final Path root = Path.of(args[index]);
      if (Files.isDirectory(root)) {
        sourceRoots.add(root);
      }
    }
    final BufferedReader requests = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    final BufferedWriter responses = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
    String line = requests.readLine();
    while (line != null) {
      final Map<String, Object> request;
      try {
        request = asObject(new Reader(line).read());
      } catch (IllegalArgumentException malformed) {
        System.err.println("malformed request");
        System.exit(MALFORMED_REQUEST);
        return;
      }
      responses.write(response(compiler, sourceRoots, request));
      responses.write('\n');
      responses.flush();
      line = requests.readLine();
    }
  }

  /** Types the spans of one request; a file that does not compile still answers what it can. */
  private static String response(
      final JavaCompiler compiler, final List<Path> sourceRoots, final Map<String, Object> request) {
    final Map<String, String> types = new LinkedHashMap<>();
    final String path = String.valueOf(request.getOrDefault("path", ""));
    final String text = String.valueOf(request.getOrDefault("text", ""));
    final List<int[]> spans = spans(request);
    if (!spans.isEmpty()) {
      try {
        typeSpans(compiler, sourceRoots, path, text, spans, types);
      } catch (IOException | URISyntaxException | RuntimeException failure) {
        System.err.println("type analysis failed for one file");
      }
    }
    final StringBuilder json = new StringBuilder("{\"types\":{");
    boolean first = true;
    for (final Map.Entry<String, String> entry : types.entrySet()) {
      json.append(first ? "" : ",").append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
      first = false;
    }
    return json.append("}}").toString();
  }

  private static void typeSpans(
      final JavaCompiler compiler,
      final List<Path> sourceRoots,
      final String path,
      final String text,
      final List<int[]> spans,
      final Map<String, String> types)
      throws IOException, URISyntaxException {
    final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      files.setLocationFromPaths(StandardLocation.SOURCE_PATH, sourceRoots);
      files.setLocationFromPaths(StandardLocation.CLASS_PATH, List.of());
      final JavaFileObject source = new Source(path, text);
      final JavacTask task =
          (JavacTask)
              compiler.getTask(null, files, diagnostics, List.of("-proc:none", "-implicit:none"), null, List.of(source));
      final Iterable<? extends CompilationUnitTree> units = task.parse();
      task.analyze();
      final Trees trees = Trees.instance(task);
      final Types model = task.getTypes();
      for (final CompilationUnitTree unit : units) {
        final List<Candidate> candidates = candidates(trees, unit);
        for (final int[] span : spans) {
          final Candidate best = innermost(candidates, span[0], span[1]);
          if (best != null) {
            final String name = typeName(model, trees.getTypeMirror(best.path()));
            if (name != null) {
              types.put(span[0] + ":" + span[1], name);
            }
          }
        }
      }
    }
  }

  /** An expression with its source range, collected once per unit. */
  private record Candidate(int start, int end, int depth, TreePath path) {}

  private static List<Candidate> candidates(final Trees trees, final CompilationUnitTree unit) {
    final SourcePositions positions = trees.getSourcePositions();
    final List<Candidate> found = new ArrayList<>();
    new TreePathScanner<Void, Void>() {
      @Override
      public Void scan(final com.sun.source.tree.Tree tree, final Void unused) {
        if (tree instanceof ExpressionTree) {
          final int start = (int) positions.getStartPosition(unit, tree);
          final int end = (int) positions.getEndPosition(unit, tree);
          final TreePath path = new TreePath(getCurrentPath(), tree);
          if (start >= 0 && end >= start) {
            found.add(new Candidate(start, end, depth(path), path));
          }
        }
        return super.scan(tree, unused);
      }
    }.scan(unit, null);
    return found;
  }

  private static int depth(final TreePath path) {
    int depth = 0;
    for (TreePath current = path; current != null; current = current.getParentPath()) {
      depth++;
    }
    return depth;
  }

  /** The smallest expression that contains the span; deeper nodes win a tie. */
  private static Candidate innermost(final List<Candidate> candidates, final int start, final int end) {
    Candidate best = null;
    for (final Candidate candidate : candidates) {
      if (candidate.start() <= start && candidate.end() >= end && (best == null || width(candidate) < width(best) || width(candidate) == width(best) && candidate.depth() > best.depth())) {
        best = candidate;
      }
    }
    return best;
  }

  private static int width(final Candidate candidate) {
    return candidate.end() - candidate.start();
  }

  /** The erased, fully qualified name, or null when the type is unknown to the compiler. */
  private static String typeName(final Types model, final TypeMirror mirror) {
    if (mirror == null || mirror.getKind() == TypeKind.ERROR || mirror.getKind() == TypeKind.NONE || mirror.getKind() == TypeKind.VOID) {
      return null;
    }
    final String name = model.erasure(mirror).toString();
    return name.contains("<any>") ? null : name;
  }

  private static List<int[]> spans(final Map<String, Object> request) {
    final List<int[]> spans = new ArrayList<>();
    if (request.get("spans") instanceof List<?> list) {
      for (final Object pair : list) {
        if (pair instanceof List<?> bounds && bounds.size() == 2) {
          spans.add(new int[] {((Number) bounds.get(0)).intValue(), ((Number) bounds.get(1)).intValue()});
        }
      }
    }
    return spans;
  }

  private static Map<String, Object> asObject(final Object value) {
    if (value instanceof Map<?, ?> map) {
      final Map<String, Object> object = new LinkedHashMap<>();
      map.forEach((key, entry) -> object.put(String.valueOf(key), entry));
      return object;
    }
    throw new IllegalArgumentException("request must be an object");
  }

  private static String quote(final String value) {
    final StringBuilder quoted = new StringBuilder("\"");
    for (final char character : value.toCharArray()) {
      switch (character) {
        case '"' -> quoted.append("\\\"");
        case '\\' -> quoted.append("\\\\");
        case '\n' -> quoted.append("\\n");
        case '\r' -> quoted.append("\\r");
        case '\t' -> quoted.append("\\t");
        default -> {
          if (character < ' ') {
            quoted.append(String.format("\\u%04x", (int) character));
          } else {
            quoted.append(character);
          }
        }
      }
    }
    return quoted.append('"').toString();
  }

  /** The source of one in-memory compilation unit, named by its project path. */
  private static final class Source extends SimpleJavaFileObject {
    private final String text;

    Source(final String path, final String text) throws URISyntaxException {
      super(new URI("string", null, "/" + path, null), Kind.SOURCE);
      this.text = text;
    }

    @Override
    public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
      return text;
    }
  }

  /** A reader for objects, arrays, strings, numbers and literals; JSON the protocol uses. */
  private static final class Reader {
    private final String text;
    private int position;

    Reader(final String text) {
      this.text = text;
    }

    Object read() {
      final Object value = value();
      skip();
      if (position != text.length()) {
        throw new IllegalArgumentException("trailing characters");
      }
      return value;
    }

    private Object value() {
      skip();
      if (position >= text.length()) {
        throw new IllegalArgumentException("unexpected end");
      }
      final char next = text.charAt(position);
      if (next == '{') {
        return object();
      }
      if (next == '[') {
        return array();
      }
      if (next == '"') {
        return string();
      }
      if (next == '-' || Character.isDigit(next)) {
        return number();
      }
      for (final String word : List.of("true", "false", "null")) {
        if (text.startsWith(word, position)) {
          position += word.length();
          return "null".equals(word) ? null : Boolean.valueOf(word);
        }
      }
      throw new IllegalArgumentException("unsupported value");
    }

    private Map<String, Object> object() {
      final Map<String, Object> result = new LinkedHashMap<>();
      expect('{');
      skip();
      if (accept('}')) {
        return result;
      }
      do {
        skip();
        final String key = string();
        skip();
        expect(':');
        result.put(key, value());
        skip();
      } while (accept(','));
      expect('}');
      return result;
    }

    private List<Object> array() {
      final List<Object> result = new ArrayList<>();
      expect('[');
      skip();
      if (accept(']')) {
        return result;
      }
      do {
        result.add(value());
        skip();
      } while (accept(','));
      expect(']');
      return result;
    }

    private String string() {
      expect('"');
      final StringBuilder value = new StringBuilder();
      while (position < text.length() && text.charAt(position) != '"') {
        final char next = text.charAt(position++);
        value.append(next == '\\' ? escaped() : next);
      }
      expect('"');
      return value.toString();
    }

    private char escaped() {
      final char code = text.charAt(position++);
      return switch (code) {
        case 'n' -> '\n';
        case 'r' -> '\r';
        case 't' -> '\t';
        case 'b' -> '\b';
        case 'f' -> '\f';
        case 'u' -> {
          final char value = (char) Integer.parseInt(text.substring(position, position + 4), 16);
          position += 4;
          yield value;
        }
        default -> code;
      };
    }

    private Integer number() {
      final int start = position;
      position++;
      while (position < text.length() && Character.isDigit(text.charAt(position))) {
        position++;
      }
      return Integer.valueOf(text.substring(start, position));
    }

    private void skip() {
      while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
        position++;
      }
    }

    private boolean accept(final char expected) {
      if (position < text.length() && text.charAt(position) == expected) {
        position++;
        return true;
      }
      return false;
    }

    private void expect(final char expected) {
      if (!accept(expected)) {
        throw new IllegalArgumentException("expected " + expected);
      }
    }
  }
}
