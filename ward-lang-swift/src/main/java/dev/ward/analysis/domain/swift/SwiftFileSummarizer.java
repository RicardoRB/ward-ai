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

  /**
   * The prefix of a used name that appears only inside a conformance or inheritance clause, so a
   * protocol used only to conform to is told apart from one used as a type. The prefix stays in the
   * used names, so no field of the shared summary changes.
   */
  public static final String CONFORMANCE_PREFIX = "conformance:";

  /** The kind of a protocol symbol in {@link FileSummary#declared()}. */
  public static final String PROTOCOL_KIND = "protocol";

  /** The kind of a function symbol in {@link FileSummary#declared()}. */
  public static final String FUNCTION_KIND = "function";

  /** The kind of a stored property symbol in {@link FileSummary#declared()}. */
  public static final String PROPERTY_KIND = "property";

  /** The kind prefix of a public or open symbol in {@link FileSummary#exports()}. */
  public static final String PUBLIC_KIND_PREFIX = "public:";

  /** The kind of a public or open type symbol in {@link FileSummary#exports()}. */
  public static final String PUBLIC_TYPE_KIND = PUBLIC_KIND_PREFIX + "type";

  /** The kind of a public or open function symbol in {@link FileSummary#exports()}. */
  public static final String PUBLIC_FUNCTION_KIND = PUBLIC_KIND_PREFIX + "function";

  /** The kind of a public or open stored property symbol in {@link FileSummary#exports()}. */
  public static final String PUBLIC_PROPERTY_KIND = PUBLIC_KIND_PREFIX + "property";

  private static final String IDENTIFIER = "Identifier";
  private static final String IMPORT = "import_declaration";
  private static final String IMPORT_PATH = "import_path";
  private static final String IMPORT_IDENTIFIER = "import_path_identifier";
  private static final String FUNCTION = "function_declaration";
  private static final String TEXT = "text";
  private static final String BODY_SUFFIX = "_body";
  private static final String QUALIFIER = ".";
  private static final String VARIABLE = "variable_declaration";
  private static final String PROTOCOL = "protocol_declaration";

  /** A protocol header with no attribute and no modifier beyond access below public. */
  private static final Pattern PLAIN_PROTOCOL_HEADER =
      Pattern.compile("\\s*((private|fileprivate|internal)\\s+)?protocol\\s*");

  /**
   * A header that only plain modifiers precede, so no protocol, superclass or attribute fixes it.
   */
  private static final Set<String> TYPE_DECLARATIONS =
      Set.of(
          "class_declaration",
          "struct_declaration",
          "enum_declaration",
          "protocol_declaration",
          "typealias_declaration");

  private static final Set<String> TYPE_NAMES =
      Set.of("class_name", "struct_name", "enum_name", "protocol_name", "typealias_name");

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
    final SwiftMemberDeclarations.Collected found = new SwiftMemberDeclarations.Collected();
    collect(tree, "", SwiftMemberDeclarations.Context.FILE, unit, found);
    final List<Reference> imports = found.imports;
    final Set<String> own =
        found.types.stream()
            .map(symbol -> lastComponent(symbol.name()))
            .collect(Collectors.toSet());
    final Map<String, Span> names = new LinkedHashMap<>();
    collectNames(tree, own, names);
    names.forEach((name, span) -> imports.add(new Reference(NAME_PREFIX + name, span)));
    final List<Symbol> exports = new ArrayList<>(found.types);
    exports.addAll(found.publics);
    final List<Symbol> declared = new ArrayList<>(declaredTypes(found));
    declared.addAll(found.functions);
    declared.addAll(found.properties);
    final Set<Span> excluded = new HashSet<>(found.declaredNames);
    final Set<String> assigned = new HashSet<>();
    SwiftUseScan.collectAssignments(tree, excluded, assigned);
    final Set<String> used = new HashSet<>(found.fixedNames);
    SwiftUseScan.collectUsed(tree, excluded, used);
    return new FileSummary(unit.path(), exports, imports, declared, used, assigned);
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
   * The declared types, with each plain protocol given the protocol kind; exports keep the kind.
   */
  private static List<Symbol> declaredTypes(final SwiftMemberDeclarations.Collected found) {
    return found.types.stream()
        .map(
            type ->
                found.protocolNames.contains(type.span())
                    ? new Symbol(type.name(), type.span(), PROTOCOL_KIND)
                    : type)
        .toList();
  }

  /**
   * Declares a protocol that no attribute or public access exposes, so only this module can use it.
   * Its name is a declaration, not a use.
   */
  private static void declareProtocol(
      final SyntaxNode node, final SourceUnit unit, final SwiftMemberDeclarations.Collected found) {
    final Optional<SyntaxNode> name = nameOf(node);
    if (name.isEmpty()) {
      return;
    }
    found.declaredNames.add(name.get().span());
    final int start = node.span().startOffset();
    final int end = name.get().span().startOffset();
    final String header =
        start <= end && end <= unit.text().length() ? unit.text().substring(start, end) : "";
    if (PLAIN_PROTOCOL_HEADER.matcher(header).matches()) {
      found.protocolNames.add(name.get().span());
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
      final SwiftMemberDeclarations.Context context,
      final SourceUnit unit,
      final SwiftMemberDeclarations.Collected found) {
    if (IMPORT.equals(node.kind())) {
      importOf(node).ifPresent(found.imports::add);
      return;
    }
    final String inner = declare(node, scope, unit, found);
    if (PROTOCOL.equals(node.kind())) {
      declareProtocol(node, unit, found);
    }
    if (FUNCTION.equals(node.kind())) {
      SwiftMemberDeclarations.declareFunction(node, context, unit, found);
    }
    if (VARIABLE.equals(node.kind()) && context == SwiftMemberDeclarations.Context.MEMBER) {
      SwiftMemberDeclarations.declareProperty(node, unit, found);
    }
    final SwiftMemberDeclarations.Context innerContext =
        SwiftMemberDeclarations.childContext(node, context);
    for (final SyntaxNode child : node.children()) {
      collect(child, inner, innerContext, unit, found);
    }
  }

  /** Records the type that {@code node} declares and returns the scope of its members. */
  private static String declare(
      final SyntaxNode node,
      final String scope,
      final SourceUnit unit,
      final SwiftMemberDeclarations.Collected found) {
    if (!TYPE_DECLARATIONS.contains(node.kind())) {
      return scope;
    }
    final Optional<SyntaxNode> name = nameOf(node);
    if (name.isEmpty()) {
      return scope;
    }
    final String qualified =
        scope.isEmpty()
            ? SwiftSyntax.textOf(name.get())
            : scope + QUALIFIER + SwiftSyntax.textOf(name.get());
    found.types.add(new Symbol(qualified, name.get().span()));
    if (SwiftMemberDeclarations.PUBLIC_HEADER
        .matcher(SwiftMemberDeclarations.header(node, name.get(), unit))
        .find()) {
      found.publics.add(new Symbol(qualified, name.get().span(), PUBLIC_TYPE_KIND));
    }
    return qualified;
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
        .map(SwiftSyntax::textOf);
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
}
