/*
 * Ward type sidecar for Java. Answers type facts for spans with javac's Trees API.
 *
 * Run by the project's java in source-launch mode:
 *   java WardJavaTypes.java <projectRoot> --classpath=<entries> <sourceRoot>...
 *
 * Protocol, one JSON object per line: a request {"path","text","spans":[[start,end],...],"kinds":
 * ["type","shape","call","annotations","overrides","throws","constant"]} gets the response
 * {"facts":{"start:end":{"type":{"name":"java.lang.String"},...}}}. Facts that do not resolve are
 * left out, and so are spans with none. A
 * malformed request ends the process with status 2. Diagnostics go to stderr only, never the source.
 * A library that is not on the class path comes back as an error type and is omitted.
 */

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
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
    String classpath = "";
    for (int index = 1; index < args.length; index++) {
      if (args[index].startsWith("--classpath=")) {
        classpath = args[index].substring("--classpath=".length());
        continue;
      }
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
      responses.write(response(compiler, sourceRoots, classpath, request));
      responses.write('\n');
      responses.flush();
      line = requests.readLine();
    }
  }

  /** Answers the facts of one request; a file that does not compile still answers what it can. */
  private static String response(
      final JavaCompiler compiler,
      final List<Path> sourceRoots,
      final String classpath,
      final Map<String, Object> request) {
    final Map<String, String> facts = new LinkedHashMap<>();
    final String path = String.valueOf(request.getOrDefault("path", ""));
    final String text = String.valueOf(request.getOrDefault("text", ""));
    final List<int[]> spans = spans(request);
    final List<String> kinds = kinds(request);
    if (!spans.isEmpty() && !kinds.isEmpty()) {
      try {
        factSpans(compiler, sourceRoots, classpath, path, text, spans, kinds, facts);
      } catch (IOException | URISyntaxException | RuntimeException failure) {
        System.err.println("type analysis failed for one file");
      }
    }
    final StringBuilder json = new StringBuilder("{\"facts\":{");
    boolean first = true;
    for (final Map.Entry<String, String> entry : facts.entrySet()) {
      json.append(first ? "" : ",").append(quote(entry.getKey())).append(':').append(entry.getValue());
      first = false;
    }
    return json.append("}}").toString();
  }

  private static void factSpans(
      final JavaCompiler compiler,
      final List<Path> sourceRoots,
      final String classpath,
      final String path,
      final String text,
      final List<int[]> spans,
      final List<String> kinds,
      final Map<String, String> facts)
      throws IOException, URISyntaxException {
    final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      files.setLocationFromPaths(StandardLocation.SOURCE_PATH, sourceRoots);
      final List<Path> entries = new ArrayList<>();
      for (final String entry : classpath.split(java.io.File.pathSeparator)) {
        if (!entry.isBlank()) {
          entries.add(Path.of(entry));
        }
      }
      files.setLocationFromPaths(StandardLocation.CLASS_PATH, entries);
      final JavaFileObject source = new Source(path, text);
      final JavacTask task =
          (JavacTask)
              compiler.getTask(null, files, diagnostics, List.of("-proc:none", "-implicit:none"), null, List.of(source));
      final Iterable<? extends CompilationUnitTree> units = task.parse();
      task.analyze();
      final Model model = new Model(Trees.instance(task), task.getTypes(), task.getElements());
      for (final CompilationUnitTree unit : units) {
        final List<Candidate> candidates = candidates(model.trees(), unit);
        for (final int[] span : spans) {
          final Candidate best = innermost(candidates, span[0], span[1]);
          final String found = best == null ? null : model.factsOf(best.path(), kinds);
          if (found != null) {
            facts.put(span[0] + ":" + span[1], found);
          }
        }
      }
    }
  }

  /** The compiler models of one analysis, with the fact builders. */
  private record Model(Trees trees, Types types, Elements elements) {

    /** The JSON object of the requested facts at {@code path}, or null when none resolved. */
    String factsOf(final TreePath path, final List<String> kinds) {
      final Map<String, String> fields = new LinkedHashMap<>();
      final Element element = elementOf(path);
      final TypeMirror mirror = path.getLeaf() instanceof ExpressionTree ? trees.getTypeMirror(path) : null;
      if (kinds.contains("type")) {
        put(fields, "type", typeRef(mirror));
      }
      if (kinds.contains("shape")) {
        put(fields, "shape", shape(element, mirror));
      }
      if (kinds.contains("call") && element instanceof ExecutableElement method && isInvocation(path)) {
        put(fields, "call", call(method));
      }
      if (kinds.contains("annotations") && element != null) {
        fields.put("annotations", stringArray(annotationNames(element)));
        fields.put("scopes", stringArray(scopeNames(element)));
      }
      if (element instanceof ExecutableElement method) {
        if (kinds.contains("overrides") && path.getLeaf() instanceof MethodTree) {
          put(fields, "overrides", array(overrides(method)));
        }
        if (kinds.contains("throws")) {
          put(fields, "thrown", array(thrown(method)));
        }
      }
      if (kinds.contains("constant")) {
        put(fields, "constant", constant(path.getLeaf(), element));
      }
      if (fields.isEmpty()) {
        return null;
      }
      final StringBuilder json = new StringBuilder("{");
      boolean first = true;
      for (final Map.Entry<String, String> field : fields.entrySet()) {
        json.append(first ? "" : ",").append(quote(field.getKey())).append(':').append(field.getValue());
        first = false;
      }
      return json.append('}').toString();
    }

    private Element elementOf(final TreePath path) {
      final com.sun.source.tree.Tree leaf = path.getLeaf();
      if (leaf instanceof ExpressionTree || leaf instanceof ClassTree || leaf instanceof MethodTree || leaf instanceof VariableTree) {
        return trees.getElement(path);
      }
      return null;
    }

    private static boolean isInvocation(final TreePath path) {
      return path.getLeaf() instanceof MethodInvocationTree || path.getLeaf() instanceof NewClassTree;
    }

    private static void put(final Map<String, String> fields, final String name, final String value) {
      if (value != null) {
        fields.put(name, value);
      }
    }

    /** A type reference; null when the type is unknown to the compiler. */
    String typeRef(final TypeMirror mirror) {
      if (mirror == null || mirror.getKind() == TypeKind.ERROR || mirror.getKind() == TypeKind.NONE) {
        return null;
      }
      int depth = 0;
      TypeMirror base = mirror;
      while (base.getKind() == TypeKind.ARRAY) {
        base = ((ArrayType) base).getComponentType();
        depth++;
      }
      final String name;
      final String arguments;
      if (base.getKind().isPrimitive() || base.getKind() == TypeKind.VOID) {
        name = base.toString();
        arguments = null;
      } else if (base.getKind() == TypeKind.ERROR) {
        return null;
      } else if (base instanceof DeclaredType declared && declared.asElement() instanceof TypeElement type) {
        name = type.getQualifiedName().toString();
        arguments = typeArguments(declared);
      } else if (base.getKind() == TypeKind.TYPEVAR || base.getKind() == TypeKind.WILDCARD || base.getKind() == TypeKind.INTERSECTION) {
        final TypeMirror erased = types.erasure(base);
        return erased.getKind() == base.getKind() ? null : typeRef(depth == 0 ? erased : types.getArrayType(erased));
      } else {
        return null;
      }
      final StringBuilder json = new StringBuilder("{\"name\":").append(quote(name));
      if (arguments != null) {
        json.append(",\"args\":").append(arguments);
      }
      if (depth > 0) {
        json.append(",\"array\":").append(depth);
      }
      if (base.getKind().isPrimitive() || base.getKind() == TypeKind.VOID) {
        json.append(",\"primitive\":true");
      }
      return json.append('}').toString();
    }

    /** The arguments as a JSON array, or null when there are none or one does not resolve. */
    private String typeArguments(final DeclaredType declared) {
      final List<String> arguments = new ArrayList<>();
      for (final TypeMirror argument : declared.getTypeArguments()) {
        final String ref = typeRef(argument);
        if (ref == null) {
          return null;
        }
        arguments.add(ref);
      }
      return arguments.isEmpty() ? null : array(arguments);
    }

    private String shape(final Element element, final TypeMirror mirror) {
      final Element declared = element instanceof TypeElement ? element : mirror instanceof DeclaredType type ? type.asElement() : null;
      if (!(declared instanceof TypeElement type)) {
        return null;
      }
      final String kind =
          switch (type.getKind()) {
            case INTERFACE -> "interface";
            case ENUM -> "enum";
            case RECORD -> "record";
            case ANNOTATION_TYPE -> "annotation";
            default -> "class";
          };
      final List<String> supertypes = new ArrayList<>();
      for (final TypeMirror supertype : types.directSupertypes(type.asType())) {
        final String ref = typeRef(supertype);
        if (ref != null) {
          supertypes.add(ref);
        }
      }
      final Set<Modifier> modifiers = type.getModifiers();
      return "{\"kind\":" + quote(kind)
          + ",\"abstract\":" + modifiers.contains(Modifier.ABSTRACT)
          + ",\"final\":" + modifiers.contains(Modifier.FINAL)
          + ",\"sealed\":" + modifiers.contains(Modifier.SEALED)
          + ",\"supertypes\":" + array(supertypes) + "}";
    }

    private String call(final ExecutableElement method) {
      if (!(method.getEnclosingElement() instanceof TypeElement owner)) {
        return null;
      }
      final List<String> parameters = new ArrayList<>();
      for (final VariableElement parameter : method.getParameters()) {
        final String ref = typeRef(types.erasure(parameter.asType()));
        if (ref == null) {
          return null;
        }
        parameters.add(ref);
      }
      final String returns = typeRef(method.getReturnType());
      return "{\"declaring\":" + typeRef(types.erasure(owner.asType()))
          + ",\"name\":" + quote(method.getSimpleName().toString())
          + ",\"params\":" + array(parameters)
          + (returns == null ? "" : ",\"returns\":" + returns)
          + ",\"static\":" + method.getModifiers().contains(Modifier.STATIC)
          + ",\"varargs\":" + method.isVarArgs() + "}";
    }

    private static List<String> annotationNames(final Element element) {
      final List<String> names = new ArrayList<>();
      for (final AnnotationMirror annotation : element.getAnnotationMirrors()) {
        names.add(annotation.getAnnotationType().toString());
      }
      return names;
    }

    /** The annotations of the enclosing declarations, nearest first. */
    private static List<String> scopeNames(final Element element) {
      final List<String> names = new ArrayList<>();
      for (Element scope = element.getEnclosingElement(); scope != null; scope = scope.getEnclosingElement()) {
        names.addAll(annotationNames(scope));
      }
      return names;
    }

    private List<String> overrides(final ExecutableElement method) {
      final List<String> overridden = new ArrayList<>();
      if (method.getEnclosingElement() instanceof TypeElement owner) {
        final Deque<TypeMirror> pending = new ArrayDeque<>(types.directSupertypes(owner.asType()));
        final Set<String> seen = new HashSet<>();
        while (!pending.isEmpty()) {
          final TypeMirror supertype = pending.pop();
          if (supertype instanceof DeclaredType declared && declared.asElement() instanceof TypeElement parent && seen.add(parent.getQualifiedName().toString())) {
            for (final Element member : parent.getEnclosedElements()) {
              if (member instanceof ExecutableElement candidate && elements.overrides(method, candidate, owner)) {
                final String target = call(candidate);
                if (target != null) {
                  overridden.add(target);
                }
              }
            }
            pending.addAll(types.directSupertypes(supertype));
          }
        }
      }
      return overridden;
    }

    /** The checked exceptions the method declares: Throwable but neither RuntimeException nor Error. */
    private List<String> thrown(final ExecutableElement method) {
      final TypeMirror runtime = elements.getTypeElement("java.lang.RuntimeException").asType();
      final TypeMirror error = elements.getTypeElement("java.lang.Error").asType();
      final List<String> checked = new ArrayList<>();
      for (final TypeMirror thrown : method.getThrownTypes()) {
        if (!types.isSubtype(thrown, runtime) && !types.isSubtype(thrown, error)) {
          final String ref = typeRef(types.erasure(thrown));
          if (ref != null) {
            checked.add(ref);
          }
        }
      }
      return checked;
    }

    /** A literal or a constant variable's value as JSON; integral values are numbers. */
    private static String constant(final com.sun.source.tree.Tree leaf, final Element element) {
      Object value = null;
      if (leaf instanceof LiteralTree literal) {
        value = literal.getValue();
      } else if (element instanceof VariableElement variable) {
        value = variable.getConstantValue();
      }
      if (value instanceof String || value instanceof Character) {
        return quote(value.toString());
      }
      if (value instanceof Boolean || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
        return value.toString();
      }
      if (value instanceof Float || value instanceof Double) {
        return Double.toString(((Number) value).doubleValue());
      }
      return null;
    }
  }

  private static String array(final List<String> rawItems) {
    return "[" + String.join(",", rawItems) + "]";
  }

  private static String stringArray(final List<String> items) {
    final List<String> quoted = new ArrayList<>();
    items.forEach(item -> quoted.add(quote(item)));
    return array(quoted);
  }

  private static List<String> kinds(final Map<String, Object> request) {
    final List<String> kinds = new ArrayList<>();
    if (request.get("kinds") instanceof List<?> list) {
      list.forEach(kind -> kinds.add(String.valueOf(kind)));
    }
    return kinds;
  }

  /** A tree with its source range, collected once per unit. */
  private record Candidate(int start, int end, int depth, TreePath path) {}

  private static List<Candidate> candidates(final Trees trees, final CompilationUnitTree unit) {
    final SourcePositions positions = trees.getSourcePositions();
    final List<Candidate> found = new ArrayList<>();
    new TreePathScanner<Void, Void>() {
      @Override
      public Void scan(final com.sun.source.tree.Tree tree, final Void unused) {
        if (tree instanceof ExpressionTree || tree instanceof ClassTree || tree instanceof MethodTree || tree instanceof VariableTree) {
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

  /** The smallest tree that contains the span; deeper nodes win a tie. */
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
