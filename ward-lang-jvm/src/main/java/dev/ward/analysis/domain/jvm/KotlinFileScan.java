package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One pass over a Kotlin file, read from its syntax tree only. The header gives the package, the
 * imports and a literal {@code @file:JvmName}. Top-level declarations are qualified by the package,
 * nested ones by their type; a file with top-level functions or properties declares its facade
 * class, which Java sees under the file name. Name references come from type positions, call chains
 * and annotations; no types are inferred.
 */
final class KotlinFileScan {
  private static final String PREAMBLE = "preamble";
  private static final String PACKAGE_HEADER = "packageHeader";
  private static final String IMPORT_LIST = "importList";
  private static final String IMPORT_HEADER = "importHeader";
  private static final String IMPORT_ALIAS = "importAlias";
  private static final String FILE_ANNOTATIONS = "fileAnnotations";
  private static final String FILE_ANNOTATION = "fileAnnotation";
  private static final String UNESCAPED_ANNOTATION = "unescapedAnnotation";
  private static final String VALUE_ARGUMENTS = "valueArguments";
  private static final String ANNOTATION = "annotation";
  private static final String USE_SITE_TARGET = "annotationUseSiteTarget";
  private static final String TOP_LEVEL = "topLevelObject";
  private static final String SIMPLE_IDENTIFIER = "simpleIdentifier";
  private static final String IDENTIFIER = "identifier";
  private static final String FUN_DECLARATION = "functionDeclaration";
  private static final Set<String> STRING_REFERENCES = Set.of("LineStrRef", "MultiLineStrRef");
  private static final String VARIABLE = "variableDeclaration";
  private static final String USER_TYPE = "userType";
  private static final String SIMPLE_USER_TYPE = "simpleUserType";
  private static final String CALLABLE_REFERENCE = "callableReference";
  private static final String POSTFIX = "postfixUnaryExpression";
  private static final String POSTFIX_OPERATION = "postfixUnaryOperation";
  private static final String ATOMIC = "atomicExpression";
  private static final String CALL_SUFFIX = "callSuffix";
  private static final String JVM_NAME = "JvmName";
  private static final String COMPANION = "Companion";
  private static final String FACADE_SUFFIX = "Kt";
  private static final String KOTLIN_SUFFIX = ".kt";
  private static final String DOT = ".";
  private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]+)\"");
  private static final Set<String> TYPE_DECLARATIONS =
      Set.of("classDeclaration", "objectDeclaration", "companionObject", "typeAlias");
  private static final Set<String> MEMBER_DECLARATIONS =
      Set.of("functionDeclaration", "propertyDeclaration");

  private final String path;
  private final List<Symbol> declared = new ArrayList<>();
  private final List<Reference> references = new ArrayList<>();
  private boolean hasTopLevelMembers;

  KotlinFileScan(final String path) {
    this.path = path;
  }

  /** Summarizes the file under {@code root}. */
  FileSummary scan(final SyntaxNode root) {
    final String packageName = readHeader(root);
    for (final SyntaxNode child : root.children()) {
      if (TOP_LEVEL.equals(child.kind())) {
        child.children().forEach(decl -> visit(decl, packageName, true));
      }
    }
    if (hasTopLevelMembers) {
      declared.add(new Symbol(JavaNames.qualify(packageName, facadeName(root)), root.span()));
    }
    return new FileSummary(path, declared, references, declared);
  }

  /** Reads the package and the imports from the preamble, and returns the package name. */
  private String readHeader(final SyntaxNode root) {
    String packageName = "";
    for (final SyntaxNode preamble : children(root, PREAMBLE)) {
      for (final SyntaxNode header : children(preamble, PACKAGE_HEADER)) {
        if (!children(header, IDENTIFIER).isEmpty()) {
          packageName = segments(children(header, IDENTIFIER).get(0));
          references.add(new Reference("package:" + packageName, header.span()));
        }
      }
      for (final SyntaxNode list : children(preamble, IMPORT_LIST)) {
        children(list, IMPORT_HEADER).forEach(this::importReferences);
      }
    }
    return packageName;
  }

  /**
   * An import names a declaration; a star import names a package; an alias also names the alias.
   */
  private void importReferences(final SyntaxNode header) {
    final String name = segments(children(header, IDENTIFIER).get(0));
    if (JavaSyntax.hasTerminal(header, "*")) {
      references.add(new Reference("on-demand:" + name, header.span()));
      return;
    }
    references.add(new Reference("import:" + name, header.span()));
    for (final SyntaxNode alias : children(header, IMPORT_ALIAS)) {
      final String simple = JavaSyntax.dotted(JavaSyntax.child(alias, SIMPLE_IDENTIFIER));
      references.add(new Reference("alias:" + simple + "=" + name, header.span()));
    }
  }

  /** Declares a declaration under {@code owner}, and visits its contents with it as the owner. */
  private void visit(final SyntaxNode node, final String owner, final boolean topLevel) {
    if (TYPE_DECLARATIONS.contains(node.kind())) {
      final String qualified = JavaNames.qualify(owner, declaredTypeName(node));
      declared.add(new Symbol(qualified, node.span()));
      visitChildren(node, qualified);
    } else if (MEMBER_DECLARATIONS.contains(node.kind())) {
      declareMember(node, owner, topLevel);
      visitChildren(node, owner);
    } else if (USER_TYPE.equals(node.kind())) {
      addName(node, userTypeName(node));
      visitChildren(node, owner);
    } else if (POSTFIX.equals(node.kind())) {
      postfix(node, owner, true);
    } else if (CALLABLE_REFERENCE.equals(node.kind())) {
      referenceOfCallable(node);
      visitChildren(node, owner);
    } else if (UNESCAPED_ANNOTATION.equals(node.kind())) {
      children(node, IDENTIFIER).forEach(id -> addName(node, segments(id)));
      visitChildren(node, owner);
    } else if (STRING_REFERENCES.contains(node.kind())) {
      addName(node, JavaSyntax.textOf(node).substring(1));
    } else if (ANNOTATION.equals(node.kind())) {
      if (children(node, USE_SITE_TARGET).isEmpty()) {
        addName(node, KotlinNameChain.of(node));
      }
      visitChildren(node, owner);
    } else {
      visitChildren(node, owner);
    }
  }

  private void visitChildren(final SyntaxNode node, final String owner) {
    for (final SyntaxNode child : node.children()) {
      visit(child, owner, false);
    }
  }

  /** The name of a class, object, companion or type alias. */
  private static String declaredTypeName(final SyntaxNode node) {
    if (node.children().stream().noneMatch(child -> SIMPLE_IDENTIFIER.equals(child.kind()))) {
      return COMPANION;
    }
    return JavaSyntax.dotted(JavaSyntax.child(node, SIMPLE_IDENTIFIER));
  }

  /** A top-level function or property is declared by its package; a member is not declared. */
  private void declareMember(final SyntaxNode node, final String owner, final boolean topLevel) {
    if (!topLevel) {
      return;
    }
    final Optional<String> name = memberName(node);
    if (name.isPresent()) {
      declared.add(new Symbol(JavaNames.qualify(owner, name.get()), node.span()));
      hasTopLevelMembers = true;
    }
  }

  private static Optional<String> memberName(final SyntaxNode node) {
    if (FUN_DECLARATION.equals(node.kind())) {
      return children(node, IDENTIFIER).stream().map(KotlinFileScan::segments).findFirst();
    }
    return children(node, VARIABLE).stream()
        .map(variable -> JavaSyntax.dotted(JavaSyntax.child(variable, SIMPLE_IDENTIFIER)))
        .findFirst();
  }

  /**
   * A callable reference names its declaration unless it starts with a type, which is read apart.
   */
  private void referenceOfCallable(final SyntaxNode node) {
    if (children(node, USER_TYPE).isEmpty()) {
      children(node, IDENTIFIER).forEach(id -> addName(node, segments(id)));
    }
  }

  /**
   * A postfix expression names its leading dotted name. A navigation call such as {@code
   * x.toMoney()} names the called member; nested chains are read once, with their owner's name.
   */
  private void postfix(final SyntaxNode node, final String owner, final boolean named) {
    if (named) {
      addName(node, KotlinNameChain.of(node));
    } else if (startsWithCall(node)) {
      addMember(node, atomName(node));
    }
    for (final SyntaxNode child : node.children()) {
      if (POSTFIX_OPERATION.equals(child.kind())) {
        for (final SyntaxNode part : child.children()) {
          if (POSTFIX.equals(part.kind())) {
            postfix(part, owner, false);
          } else {
            visit(part, owner, false);
          }
        }
      } else {
        visit(child, owner, false);
      }
    }
  }

  private static boolean startsWithCall(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> POSTFIX_OPERATION.equals(child.kind()))
        .findFirst()
        .filter(op -> !children(op, CALL_SUFFIX).isEmpty())
        .isPresent();
  }

  private static String atomName(final SyntaxNode node) {
    return children(node, ATOMIC).stream()
        .flatMap(atom -> children(atom, SIMPLE_IDENTIFIER).stream())
        .map(JavaSyntax::dotted)
        .findFirst()
        .orElse("");
  }

  private static String userTypeName(final SyntaxNode node) {
    final List<String> parts = new ArrayList<>();
    for (final SyntaxNode simple : children(node, SIMPLE_USER_TYPE)) {
      parts.add(segments(simple));
    }
    return String.join(DOT, parts);
  }

  private void addName(final SyntaxNode node, final String name) {
    if (!name.isEmpty()) {
      references.add(new Reference("name:" + name, node.span()));
    }
  }

  private void addMember(final SyntaxNode node, final String name) {
    if (!name.isEmpty()) {
      references.add(new Reference("member:" + name, node.span()));
    }
  }

  /**
   * The facade class: a literal {@code @file:JvmName} value, else the file name with {@code Kt}.
   */
  private String facadeName(final SyntaxNode root) {
    return jvmName(root).orElseGet(this::fileFacadeName);
  }

  private String fileFacadeName() {
    final String file = path.substring(path.lastIndexOf('/') + 1);
    final String base =
        file.endsWith(KOTLIN_SUFFIX)
            ? file.substring(0, file.length() - KOTLIN_SUFFIX.length())
            : file;
    return Character.toUpperCase(base.charAt(0)) + base.substring(1) + FACADE_SUFFIX;
  }

  private static Optional<String> jvmName(final SyntaxNode root) {
    return children(root, PREAMBLE).stream()
        .flatMap(preamble -> children(preamble, FILE_ANNOTATIONS).stream())
        .flatMap(annotations -> children(annotations, FILE_ANNOTATION).stream())
        .flatMap(annotation -> children(annotation, UNESCAPED_ANNOTATION).stream())
        .map(KotlinFileScan::jvmNameArgument)
        .flatMap(Optional::stream)
        .findFirst();
  }

  /**
   * Returns the string argument of a {@code JvmName} annotation, or none for another annotation.
   */
  private static Optional<String> jvmNameArgument(final SyntaxNode annotation) {
    final boolean named =
        children(annotation, IDENTIFIER).stream()
            .map(KotlinFileScan::segments)
            .anyMatch(JVM_NAME::equals);
    if (!named) {
      return Optional.empty();
    }
    return children(annotation, VALUE_ARGUMENTS).stream()
        .map(JavaSyntax::dotted)
        .map(STRING_LITERAL::matcher)
        .filter(Matcher::find)
        .map(match -> match.group(1))
        .findFirst();
  }

  /** The simple names of a dotted identifier, joined with dots. */
  private static String segments(final SyntaxNode node) {
    final List<String> parts = new ArrayList<>();
    for (final SyntaxNode child : children(node, SIMPLE_IDENTIFIER)) {
      parts.add(JavaSyntax.dotted(child));
    }
    return String.join(DOT, parts);
  }

  private static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).toList();
  }
}
