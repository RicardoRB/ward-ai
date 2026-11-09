package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.FileSummarizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Summarizes a Swift file from its syntax tree: the modules it imports, the identifiers it uses and
 * the types it declares, with nested types qualified by their enclosing type. It also declares the
 * functions that no protocol, superclass or attribute can fix, and records every identifier outside
 * the names of functions, so the whole-run unused rule can see uses across files.
 */
public final class SwiftFileSummarizer implements FileSummarizer {

  /** The specifier prefix of an import, so the resolver can tell modules from other references. */
  public static final String MODULE_PREFIX = "module:";

  /** The specifier prefix of a name use, which the resolver matches against declared types. */
  public static final String NAME_PREFIX = "name:";

  /** The kind of a function symbol in {@link FileSummary#declared()}. */
  public static final String FUNCTION_KIND = "function";

  /** The kind of a stored property symbol in {@link FileSummary#declared()}. */
  public static final String PROPERTY_KIND = "property";

  private static final String IDENTIFIER = "Identifier";
  private static final String IMPORT = "import_declaration";
  private static final String IMPORT_PATH = "import_path";
  private static final String IMPORT_IDENTIFIER = "import_path_identifier";
  private static final String FUNCTION = "function_declaration";
  private static final String FUNCTION_NAME = "function_name";
  private static final String TEXT = "text";
  private static final String BODY_SUFFIX = "_body";
  private static final String QUALIFIER = ".";
  private static final String INHERITANCE = "type_inheritance_clause";
  private static final String VARIABLE = "variable_declaration";
  private static final String VARIABLE_NAME = "variable_name";
  private static final String PATTERN = "pattern";
  private static final String PATTERN_INITIALIZER_LIST = "pattern_initializer_list";
  private static final String PATTERN_INITIALIZER = "pattern_initializer";
  private static final String OPEN_BRACE = "{";

  /**
   * A header that only plain modifiers precede, so no protocol, superclass or attribute fixes it.
   */
  private static final Pattern PLAIN_FUNCTION_HEADER =
      Pattern.compile(
          "\\s*((private|fileprivate|internal|static|final|mutating|nonmutating)\\s+)*func\\s*");

  /** A stored property header with plain modifiers and {@code var}, which no attribute fixes. */
  private static final Pattern PLAIN_VARIABLE_HEADER =
      Pattern.compile("\\s*((private|fileprivate|internal|static|final)\\s+)*var\\s*");

  private static final Set<String> TYPE_DECLARATIONS =
      Set.of(
          "class_declaration",
          "struct_declaration",
          "enum_declaration",
          "protocol_declaration",
          "typealias_declaration");
  private static final Set<String> TYPE_NAMES =
      Set.of("class_name", "struct_name", "enum_name", "protocol_name", "typealias_name");

  /** The type declarations, whose bodies open a scope of members. */
  private static final Set<String> TYPE_KINDS =
      Set.of(
          "class_declaration",
          "struct_declaration",
          "enum_declaration",
          "protocol_declaration",
          "extension_declaration",
          "actor_declaration");

  /** The nodes whose bodies hold local declarations, which no member rule may declare. */
  private static final Set<String> BLOCK_KINDS =
      Set.of(
          FUNCTION,
          "code_block",
          "closure_expression",
          "initializer_declaration",
          "deinitializer_declaration",
          "subscript_declaration");

  /** Types whose members a conformance or an extension may fix, whatever their inheritance. */
  private static final Set<String> OPEN_BODIES =
      Set.of("protocol_declaration", "extension_declaration", "actor_declaration");

  /**
   * Summarizes the unit. Imports become {@code module:<Name>} references, and every declared type
   * is both an export and a declaration, since Swift exports all of them to the module. Each
   * distinct identifier the file uses becomes a {@code name:<Identifier>} reference, except its own
   * declared names; comments and string literals hold no identifier nodes, so they add none.
   * Functions are declared, but not exported, when no enclosing type inherits, conforms or is
   * extended, and when their header carries no attribute or {@code override}. The used names are
   * every identifier outside imports and outside function names, so a use in the same file counts.
   */
  @Override
  public FileSummary summarize(final SourceUnit unit, final SyntaxNode tree) {
    final Collected found = new Collected();
    collect(tree, "", Context.FILE, unit, found);
    final List<Reference> imports = found.imports;
    final Set<String> own =
        found.types.stream()
            .map(symbol -> lastComponent(symbol.name()))
            .collect(Collectors.toSet());
    final Map<String, Span> names = new LinkedHashMap<>();
    collectNames(tree, own, names);
    names.forEach((name, span) -> imports.add(new Reference(NAME_PREFIX + name, span)));
    final List<Symbol> declared = new ArrayList<>(found.types);
    declared.addAll(found.functions);
    declared.addAll(found.properties);
    final Set<Span> excluded = new HashSet<>(found.declaredNames);
    final Set<String> assigned = new HashSet<>();
    collectAssignments(tree, excluded, assigned);
    final Set<String> used = new HashSet<>(found.fixedNames);
    collectUsed(tree, excluded, used);
    return new FileSummary(unit.path(), found.types, imports, declared, used, assigned);
  }

  /** Records the first use of each identifier outside imports and the file's own names. */
  private static void collectNames(
      final SyntaxNode node, final Set<String> own, final Map<String, Span> names) {
    if (IMPORT.equals(node.kind())) {
      return;
    }
    final String name = node.fields().get(TEXT);
    if (IDENTIFIER.equals(node.kind()) && name != null && !own.contains(name)) {
      names.putIfAbsent(name, node.span());
    }
    for (final SyntaxNode child : node.children()) {
      collectNames(child, own, names);
    }
  }

  /**
   * Records the target of each plain assignment: its name is assigned, and its span is not a use. A
   * compound assignment reads its target, so it is left as a use.
   */
  private static void collectAssignments(
      final SyntaxNode node, final Set<Span> excluded, final Set<String> assigned) {
    if (IMPORT.equals(node.kind())) {
      return;
    }
    if ("expression".equals(node.kind())) {
      assignedTarget(node)
          .ifPresent(
              target -> {
                excluded.add(target.span());
                assigned.add(textOf(target));
              });
    }
    for (final SyntaxNode child : node.children()) {
      collectAssignments(child, excluded, assigned);
    }
  }

  /** The plain name a plain {@code =} assigns to, if the expression is one. */
  private static Optional<SyntaxNode> assignedTarget(final SyntaxNode expression) {
    final SyntaxNode target = child(expression, "prefix_expression").orElse(null);
    final SyntaxNode operators = child(expression, "binary_expressions").orElse(null);
    if (target == null
        || operators == null
        || !"=".equals(SwiftScopeExpressions.assignment(operators))) {
      return Optional.empty();
    }
    return Optional.ofNullable(SwiftScopeExpressions.plainName(target));
  }

  /** Records every identifier outside imports and outside the excluded spans. */
  private static void collectUsed(
      final SyntaxNode node, final Set<Span> excluded, final Set<String> used) {
    if (IMPORT.equals(node.kind())) {
      return;
    }
    final String name = node.fields().get(TEXT);
    if (IDENTIFIER.equals(node.kind()) && name != null && !excluded.contains(node.span())) {
      used.add(name);
    }
    for (final SyntaxNode child : node.children()) {
      collectUsed(child, excluded, used);
    }
  }

  /** The last dot-separated part of a qualified name: the name a use of the type writes. */
  static String lastComponent(final String qualified) {
    return qualified.substring(qualified.lastIndexOf(QUALIFIER) + 1);
  }

  /**
   * Walks the tree. The context of a node says what its declarations may be: a function, a stored
   * property or nothing, so a local or a member of a type a protocol or superclass may fix is never
   * declared.
   */
  private static void collect(
      final SyntaxNode node,
      final String scope,
      final Context context,
      final SourceUnit unit,
      final Collected found) {
    if (IMPORT.equals(node.kind())) {
      importOf(node).ifPresent(found.imports::add);
      return;
    }
    final String inner = declare(node, scope, found.types);
    if (FUNCTION.equals(node.kind())) {
      declareFunction(node, context, unit, found);
    }
    if (VARIABLE.equals(node.kind()) && context == Context.MEMBER) {
      declareProperty(node, unit, found);
    }
    final Context innerContext = childContext(node, context);
    for (final SyntaxNode child : node.children()) {
      collect(child, inner, innerContext, unit, found);
    }
  }

  /** The context of the children of {@code node}, given the context {@code node} has. */
  private static Context childContext(final SyntaxNode node, final Context context) {
    if (context == Context.BLOCKED) {
      return Context.BLOCKED;
    }
    if (TYPE_KINDS.contains(node.kind())) {
      return opensBody(node) || context == Context.LOCAL ? Context.BLOCKED : Context.MEMBER;
    }
    if (BLOCK_KINDS.contains(node.kind())) {
      return Context.LOCAL;
    }
    return context;
  }

  /**
   * Whether the members of {@code node} may be fixed by a conformance, an extension or a subclass.
   */
  private static boolean opensBody(final SyntaxNode node) {
    return OPEN_BODIES.contains(node.kind())
        || TYPE_DECLARATIONS.contains(node.kind()) && child(node, INHERITANCE).isPresent();
  }

  /**
   * Declares a function whose context is not blocked and whose header carries only plain modifiers.
   * Any other function keeps its name as used, since a conformance or a call through a superclass
   * may reach it without spelling the name.
   */
  private static void declareFunction(
      final SyntaxNode node, final Context context, final SourceUnit unit, final Collected found) {
    final Optional<SyntaxNode> name = child(node, FUNCTION_NAME);
    if (name.isEmpty()) {
      return;
    }
    found.declaredNames.add(name.get().span());
    final String header = header(node, name.get(), unit);
    if (context != Context.BLOCKED && PLAIN_FUNCTION_HEADER.matcher(header).matches()) {
      found.functions.add(new Symbol(textOf(name.get()), name.get().span(), FUNCTION_KIND));
    } else {
      found.fixedNames.add(textOf(name.get()));
    }
  }

  /**
   * Declares a stored property of a closed type, with plain modifiers, no attribute and no braces,
   * so no observer, accessor or wrapper makes a read or write that the names miss.
   */
  private static void declareProperty(
      final SyntaxNode node, final SourceUnit unit, final Collected found) {
    final Optional<SyntaxNode> name = child(node, VARIABLE_NAME).or(() -> simplePatternName(node));
    if (name.isEmpty()) {
      return;
    }
    found.declaredNames.add(name.get().span());
    final int end = name.get().span().startOffset();
    final int stop = node.span().endOffset();
    final String rest =
        end <= stop && stop <= unit.text().length() ? unit.text().substring(end, stop) : "";
    if (PLAIN_VARIABLE_HEADER.matcher(header(node, name.get(), unit)).matches()
        && !rest.contains(OPEN_BRACE)) {
      found.properties.add(new Symbol(textOf(name.get()), name.get().span(), PROPERTY_KIND));
    }
  }

  /**
   * The name of a declaration with one pattern and no type annotation, such as {@code var x = 0}:
   * the pattern is the bare identifier.
   */
  private static Optional<SyntaxNode> simplePatternName(final SyntaxNode declaration) {
    final List<SyntaxNode> initializers =
        child(declaration, PATTERN_INITIALIZER_LIST)
            .map(
                list ->
                    list.children().stream()
                        .filter(c -> PATTERN_INITIALIZER.equals(c.kind()))
                        .toList())
            .orElse(List.of());
    if (initializers.size() != 1) {
      return Optional.empty();
    }
    final Optional<SyntaxNode> pattern = child(initializers.get(0), PATTERN);
    return pattern
        .flatMap(SwiftFileSummarizer::firstIdentifier)
        .filter(identifier -> textOf(identifier).equals(textOf(pattern.get())));
  }

  /** The first identifier under {@code node}, in source order, or empty. */
  private static Optional<SyntaxNode> firstIdentifier(final SyntaxNode node) {
    if (IDENTIFIER.equals(node.kind())) {
      return Optional.of(node);
    }
    for (final SyntaxNode child : node.children()) {
      final Optional<SyntaxNode> found = firstIdentifier(child);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  /** The source text from the start of {@code node} up to its name, or empty when it is unsafe. */
  private static String header(
      final SyntaxNode node, final SyntaxNode name, final SourceUnit unit) {
    final int start = node.span().startOffset();
    final int end = name.span().startOffset();
    return start <= end && end <= unit.text().length() ? unit.text().substring(start, end) : "";
  }

  /** Records the type that {@code node} declares and returns the scope of its members. */
  private static String declare(
      final SyntaxNode node, final String scope, final List<Symbol> declared) {
    if (!TYPE_DECLARATIONS.contains(node.kind())) {
      return scope;
    }
    final Optional<SyntaxNode> name = nameOf(node);
    if (name.isEmpty()) {
      return scope;
    }
    final String qualified =
        scope.isEmpty() ? textOf(name.get()) : scope + QUALIFIER + textOf(name.get());
    declared.add(new Symbol(qualified, name.get().span()));
    return qualified;
  }

  private static Optional<SyntaxNode> child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(c -> kind.equals(c.kind())).findFirst();
  }

  private static Optional<Reference> importOf(final SyntaxNode importDeclaration) {
    final Optional<SyntaxNode> path =
        importDeclaration.children().stream()
            .filter(child -> IMPORT_PATH.equals(child.kind()))
            .findFirst();
    return path.flatMap(SwiftFileSummarizer::moduleReference)
        .map(module -> new Reference(MODULE_PREFIX + module, importDeclaration.span()));
  }

  /** The first identifier of an import path is the module, whatever the path continues with. */
  private static Optional<String> moduleReference(final SyntaxNode importPath) {
    return importPath.children().stream()
        .filter(child -> IMPORT_IDENTIFIER.equals(child.kind()))
        .findFirst()
        .map(SwiftFileSummarizer::textOf);
  }

  /** The name of a type declaration: the first name node, without entering its body. */
  private static Optional<SyntaxNode> nameOf(final SyntaxNode declaration) {
    for (final SyntaxNode child : declaration.children()) {
      if (TYPE_NAMES.contains(child.kind())) {
        return Optional.of(child);
      }
      if (!child.kind().endsWith(BODY_SUFFIX)) {
        final Optional<SyntaxNode> nested = nameOf(child);
        if (nested.isPresent()) {
          return nested;
        }
      }
    }
    return Optional.empty();
  }

  /** The source text of a subtree, concatenated from its terminals in order. */
  private static String textOf(final SyntaxNode node) {
    final StringBuilder text = new StringBuilder();
    appendText(node, text);
    return text.toString();
  }

  private static void appendText(final SyntaxNode node, final StringBuilder text) {
    final String token = node.fields().get(TEXT);
    if (token != null) {
      text.append(token);
    }
    for (final SyntaxNode child : node.children()) {
      appendText(child, text);
    }
  }

  /** What the declarations under a node may be declared as. */
  private enum Context {
    /** The file itself: functions may be declared, properties are not. */
    FILE,
    /** The body of a type that nothing fixes: functions and stored properties may be declared. */
    MEMBER,
    /** Inside a function or a closure: a local, so nothing is declared. */
    LOCAL,
    /** Inside a type a protocol, superclass or extension may fix: nothing is declared. */
    BLOCKED
  }

  /** The symbols and names one walk of a unit collects. */
  private static final class Collected {
    private final List<Reference> imports = new ArrayList<>();
    private final List<Symbol> types = new ArrayList<>();
    private final List<Symbol> functions = new ArrayList<>();
    private final List<Symbol> properties = new ArrayList<>();

    /** The spans of declared function and variable names: a declaration is not a use. */
    private final Set<Span> declaredNames = new HashSet<>();

    /** The names of functions a protocol, superclass or attribute may fix: always used. */
    private final Set<String> fixedNames = new HashSet<>();
  }
}
