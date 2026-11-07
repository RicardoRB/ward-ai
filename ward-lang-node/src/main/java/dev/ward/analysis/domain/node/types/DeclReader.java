package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeImportBindings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the declaration index of a file from its parse tree: what it declares, imports, exports and
 * what its reference directives name. Statements the reader does not understand are skipped.
 */
public final class DeclReader {
  private static final Pattern REFERENCE =
      Pattern.compile("^///\\s*<reference\\s+(lib|path|types)\\s*=\\s*[\"']([^\"']+)[\"']");
  private static final String GLOBAL = "*global*";
  private static final String STATEMENT = "statement";
  private static final String EXPORT = "export";
  private static final String DEFAULT = "default";
  private static final String STAR = "*";
  private static final String IDENTIFIER = "identifier";
  private static final String TYPE_PARAMETERS = "typeParameters";
  private static final String TYPE = "type_";
  private static final String EXPRESSION_SEQUENCE = "expressionSequence";

  private final List<Decl> decls = new ArrayList<>();
  private final List<FileDecls.Export> exports = new ArrayList<>();
  private final Map<String, List<Decl>> modules = new LinkedHashMap<>();

  private DeclReader() {}

  /**
   * Reads the file.
   *
   * @param tree the parse tree of the file
   * @param text the text of the file, for its reference directives
   * @return the declaration index
   */
  public static FileDecls read(final SyntaxNode tree, final String text) {
    final DeclReader reader = new DeclReader();
    reader.elements(tree, reader.decls, reader.exports);
    final Map<String, FileDecls.Import> imports = new HashMap<>();
    for (final NodeImportBindings.Import binding : NodeImportBindings.of(tree)) {
      imports.put(binding.local(), new FileDecls.Import(binding.specifier(), binding.member()));
    }
    final boolean isModule =
        !imports.isEmpty()
            || !reader.exports.isEmpty()
            || reader.decls.stream().anyMatch(decl -> decl.has(Decl.EXPORTED));
    return new FileDecls(
        isModule, imports, reader.decls, reader.exports, reader.modules, references(text));
  }

  private static List<String> references(final String text) {
    final List<String> found = new ArrayList<>();
    for (final String line : text.split("\n", -1)) {
      final String trimmed = line.trim();
      if (!trimmed.isEmpty()
          && !trimmed.startsWith("//")
          && !trimmed.startsWith("/*")
          && !trimmed.startsWith("*")) {
        break;
      }
      final Matcher matcher = REFERENCE.matcher(trimmed);
      if (matcher.find()) {
        found.add(matcher.group(1) + ":" + matcher.group(2));
      }
    }
    return found;
  }

  private void elements(
      final SyntaxNode node, final List<Decl> into, final List<FileDecls.Export> exportsInto) {
    for (final SyntaxNode part : TreeKit.parts(node)) {
      switch (part.kind()) {
        case "sourceElements", "statementList" -> elements(part, into, exportsInto);
        case "sourceElement" -> sourceElement(part, into, exportsInto);
        case STATEMENT -> statement(part, 0, into, exportsInto);
        default -> {
          // tokens and the end of file declare nothing
        }
      }
    }
  }

  private void sourceElement(
      final SyntaxNode element, final List<Decl> into, final List<FileDecls.Export> exportsInto) {
    final boolean exported = TreeKit.hasToken(element, EXPORT);
    final SyntaxNode statement = TreeKit.child(element, STATEMENT);
    if (statement != null) {
      statement(statement, exported ? Decl.EXPORTED : 0, into, exportsInto);
    }
  }

  private void statement(
      final SyntaxNode statement,
      final int flags,
      final List<Decl> into,
      final List<FileDecls.Export> exportsInto) {
    final boolean exported = TreeKit.hasToken(statement, EXPORT) || flags != 0;
    final int all = exported ? Decl.EXPORTED : 0;
    for (final SyntaxNode part : TreeKit.parts(statement)) {
      switch (part.kind()) {
        case STATEMENT -> statement(part, all, into, exportsInto);
        case "exportStatement" -> exportStatement(part, into, exportsInto);
        case "expressionStatement" -> expressionStatement(part, flags, into, exportsInto);
        case "variableStatement",
            "functionDeclaration",
            "classDeclaration",
            "interfaceDeclaration",
            "typeAliasDeclaration",
            "enumDeclaration",
            "namespaceDeclaration" ->
            declaration(part, all, into);
        default -> {
          // imports are read from the whole tree
        }
      }
    }
  }

  private void declaration(final SyntaxNode node, final int flags, final List<Decl> into) {
    switch (node.kind()) {
      case "variableStatement" -> into.addAll(variables(node, flags));
      case "functionDeclaration" -> into.add(function(node, flags));
      case "classDeclaration" -> into.add(clazz(node, flags));
      case "interfaceDeclaration" -> into.add(iface(node, flags));
      case "typeAliasDeclaration" -> into.add(alias(node, flags));
      case "enumDeclaration" -> into.add(enumeration(node, flags));
      default -> namespace(node, flags, into);
    }
  }

  private void exportStatement(
      final SyntaxNode node, final List<Decl> into, final List<FileDecls.Export> exportsInto) {
    final boolean isDefault = TreeKit.hasToken(node, DEFAULT);
    final int flags = Decl.EXPORTED | (isDefault ? Decl.DEFAULT : 0);
    final SyntaxNode block = TreeKit.child(node, "exportFromBlock");
    final SyntaxNode declaration = TreeKit.child(node, "declaration");
    if (block != null) {
      exportBlock(block, exportsInto);
    } else if (declaration != null) {
      TreeKit.parts(declaration).forEach(part -> declaration(part, flags, into));
    } else if (isDefault) {
      defaultExpression(TreeKit.child(node, "singleExpression"), flags, into, exportsInto);
    }
  }

  private void exportBlock(final SyntaxNode block, final List<FileDecls.Export> exportsInto) {
    final SyntaxNode from = TreeKit.child(block, "importFrom");
    final String specifier = from == null ? "" : TreeKit.unquote(stringOf(from));
    final SyntaxNode namespace = TreeKit.child(block, "importNamespace");
    if (namespace != null) {
      final List<String> leaves = TreeKit.leaves(namespace);
      final String exported = leaves.size() > 1 ? leaves.get(leaves.size() - 1) : STAR;
      exportsInto.add(new FileDecls.Export(exported, STAR, specifier));
      return;
    }
    final SyntaxNode items = TreeKit.child(block, "exportModuleItems");
    if (items != null) {
      for (final SyntaxNode item : TreeKit.children(items, "exportAliasName")) {
        final List<SyntaxNode> names = TreeKit.children(item, "moduleExportName");
        final String local = TreeKit.unquote(TreeKit.text(names.get(0)));
        final String exported = TreeKit.unquote(TreeKit.text(names.get(names.size() - 1)));
        exportsInto.add(new FileDecls.Export(exported, local, specifier));
      }
    }
  }

  private static String stringOf(final SyntaxNode from) {
    return TreeKit.leaves(from).stream()
        .filter(leaf -> leaf.length() > 1 && "\"'".indexOf(leaf.charAt(0)) >= 0)
        .findFirst()
        .orElse("");
  }

  /** Reads {@code export default <expression>} and the {@code export = x} forms of the grammar. */
  private void expressionStatement(
      final SyntaxNode node,
      final int flags,
      final List<Decl> into,
      final List<FileDecls.Export> exportsInto) {
    final SyntaxNode sequence = TreeKit.child(node, EXPRESSION_SEQUENCE);
    final SyntaxNode expression =
        sequence == null ? null : TreeKit.child(sequence, "singleExpression");
    if (expression == null) {
      return;
    }
    final List<SyntaxNode> parts = TreeKit.parts(expression);
    if (parts.size() == 2 && DEFAULT.equals(TreeKit.text(parts.get(0)))) {
      defaultExpression(parts.get(1), flags | Decl.EXPORTED | Decl.DEFAULT, into, exportsInto);
    } else if (parts.size() == 3
        && EXPORT.equals(TreeKit.text(parts.get(0)))
        && "=".equals(TreeKit.text(parts.get(1)))) {
      exportsInto.add(new FileDecls.Export("=", TreeKit.text(parts.get(2)), ""));
    }
  }

  private void defaultExpression(
      final SyntaxNode expression,
      final int flags,
      final List<Decl> into,
      final List<FileDecls.Export> exportsInto) {
    if (expression == null) {
      return;
    }
    final SyntaxNode function = TreeKit.find(expression, "functionDeclaration");
    if (function != null && TreeKit.child(expression, "anonymousFunction") != null) {
      into.add(function(function, flags));
    } else if (TreeKit.hasToken(expression, "class")) {
      into.add(clazz(expression, flags));
    } else {
      exportsInto.add(new FileDecls.Export(DEFAULT, TreeKit.text(expression), ""));
    }
  }

  private List<Decl> variables(final SyntaxNode node, final int flags) {
    final SyntaxNode modifier = TreeKit.child(node, "varModifier");
    final boolean isConst = modifier != null && "const".equals(TreeKit.text(modifier));
    final SyntaxNode list = TreeKit.child(node, "variableDeclarationList");
    final List<Decl> found = new ArrayList<>();
    if (list == null) {
      return found;
    }
    for (final SyntaxNode declaration : TreeKit.children(list, "variableDeclaration")) {
      final SyntaxNode name = TreeKit.child(declaration, "identifierOrKeyWord");
      if (name != null) {
        found.add(variable(declaration, TreeKit.text(name), flags | (isConst ? Decl.CONST : 0)));
      }
    }
    return found;
  }

  private static Decl variable(final SyntaxNode declaration, final String name, final int flags) {
    final SyntaxNode annotation = TreeKit.child(declaration, "typeAnnotation");
    Ty type = SigReader.annotated(annotation);
    if (annotation == null && TreeKit.hasToken(declaration, "=")) {
      final List<SyntaxNode> expressions = TreeKit.children(declaration, "singleExpression");
      type = SimpleInit.of(expressions.get(expressions.size() - 1), (flags & Decl.CONST) != 0);
    }
    return new Decl(Decl.Kind.VARIABLE, name, Decl.Header.NONE, List.of(), type, List.of(), flags);
  }

  private static Decl function(final SyntaxNode node, final int flags) {
    final SyntaxNode signature = TreeKit.child(node, "callSignature");
    final Sig sig = SigReader.of(signature == null ? node : signature);
    final Decl.Header header =
        new Decl.Header(sig.typeParams(), List.of(), List.of(), List.of(sig), List.of(), List.of());
    return new Decl(
        Decl.Kind.FUNCTION,
        TreeKit.text(identifier(node)),
        header,
        List.of(),
        Ty.UNKNOWN,
        List.of(),
        flags);
  }

  private static SyntaxNode identifier(final SyntaxNode node) {
    final SyntaxNode identifier = TreeKit.child(node, IDENTIFIER);
    return identifier == null ? node : identifier;
  }

  private static Decl clazz(final SyntaxNode node, final int flags) {
    final MemberReader members = new MemberReader();
    final SyntaxNode tail = TreeKit.child(node, "classTail");
    if (tail != null) {
      members.classTail(tail);
    }
    final SyntaxNode heritage = TreeKit.child(node, "classHeritage");
    final List<Ty> extended = new ArrayList<>();
    final List<Ty> implemented = new ArrayList<>();
    if (heritage != null) {
      final SyntaxNode extendsClause = TreeKit.child(heritage, "classExtendsClause");
      if (extendsClause != null) {
        extended.addAll(typeReferences(extendsClause));
      }
      final SyntaxNode implementsClause = TreeKit.child(heritage, "implementsClause");
      if (implementsClause != null) {
        implemented.addAll(typeReferences(implementsClause));
      }
    }
    final Decl.Header header =
        new Decl.Header(
            SigReader.typeParams(TreeKit.child(node, TYPE_PARAMETERS)),
            extended,
            implemented,
            List.of(),
            members.constructs(),
            MemberReader.decorators(TreeKit.child(node, "decoratorList")));
    final int abstractFlag = TreeKit.hasToken(node, "abstract") ? Decl.ABSTRACT : 0;
    final SyntaxNode name = TreeKit.child(node, IDENTIFIER);
    return new Decl(
        Decl.Kind.CLASS,
        name == null ? DEFAULT : TreeKit.text(name),
        header,
        members.members(),
        Ty.UNKNOWN,
        List.of(),
        flags | abstractFlag);
  }

  private static List<Ty> typeReferences(final SyntaxNode clause) {
    final List<Ty> types = new ArrayList<>();
    for (final SyntaxNode reference : TreeKit.findAll(clause, "typeReference")) {
      types.add(TyParser.parse(TreeKit.leaves(reference)));
    }
    return types;
  }

  private static Decl iface(final SyntaxNode node, final int flags) {
    final MemberReader members = new MemberReader();
    final SyntaxNode body = TreeKit.child(node, "objectType");
    if (body != null) {
      members.objectType(body);
    }
    final SyntaxNode extendsClause = TreeKit.child(node, "interfaceExtendsClause");
    final Decl.Header header =
        new Decl.Header(
            SigReader.typeParams(TreeKit.child(node, TYPE_PARAMETERS)),
            extendsClause == null ? List.of() : typeReferences(extendsClause),
            List.of(),
            members.calls(),
            members.constructs(),
            List.of());
    return new Decl(
        Decl.Kind.INTERFACE,
        TreeKit.text(identifier(node)),
        header,
        members.members(),
        Ty.UNKNOWN,
        List.of(),
        flags);
  }

  private static Decl alias(final SyntaxNode node, final int flags) {
    final SyntaxNode type = TreeKit.child(node, TYPE);
    final Decl.Header header =
        new Decl.Header(
            SigReader.typeParams(TreeKit.child(node, TYPE_PARAMETERS)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    return new Decl(
        Decl.Kind.ALIAS,
        TreeKit.text(identifier(node)),
        header,
        List.of(),
        type == null ? Ty.UNKNOWN : TyParser.parse(TreeKit.leaves(type)),
        List.of(),
        flags);
  }

  private static Decl enumeration(final SyntaxNode node, final int flags) {
    final String name = TreeKit.text(identifier(node));
    final MemberReader members = new MemberReader();
    members.enumBody(TreeKit.child(node, "enumBody"), name);
    return new Decl(
        Decl.Kind.ENUM, name, Decl.Header.NONE, members.members(), Ty.UNKNOWN, List.of(), flags);
  }

  private void namespace(final SyntaxNode node, final int flags, final List<Decl> into) {
    final List<Decl> body = new ArrayList<>();
    final List<FileDecls.Export> ignored = new ArrayList<>();
    final SyntaxNode statements = TreeKit.child(node, "statementList");
    if (statements != null) {
      elements(statements, body, ignored);
    }
    final SyntaxNode name = TreeKit.child(node, "namespaceName");
    final SyntaxNode literal = literalName(node);
    if (literal != null) {
      modules
          .computeIfAbsent(TreeKit.unquote(TreeKit.text(literal)), key -> new ArrayList<>())
          .addAll(body);
    } else if (name != null) {
      into.add(
          nested(
              TreeKit.children(name, IDENTIFIER).stream().map(TreeKit::text).toList(),
              body,
              flags));
    } else {
      modules.computeIfAbsent(GLOBAL, key -> new ArrayList<>()).addAll(body);
    }
  }

  private static SyntaxNode literalName(final SyntaxNode node) {
    for (final SyntaxNode part : TreeKit.parts(node)) {
      if (part.children().isEmpty() && "StringLiteral".equals(part.kind())) {
        return part;
      }
    }
    return null;
  }

  private static Decl nested(final List<String> names, final List<Decl> body, final int flags) {
    Decl current =
        new Decl(
            Decl.Kind.NAMESPACE,
            names.get(names.size() - 1),
            Decl.Header.NONE,
            List.of(),
            Ty.UNKNOWN,
            body,
            flags);
    for (int index = names.size() - 2; index >= 0; index--) {
      current =
          new Decl(
              Decl.Kind.NAMESPACE,
              names.get(index),
              Decl.Header.NONE,
              List.of(),
              Ty.UNKNOWN,
              List.of(current),
              flags);
    }
    return current;
  }

  /** The key of the ambient declarations that augment the global scope. */
  public static String globalKey() {
    return GLOBAL;
  }
}
