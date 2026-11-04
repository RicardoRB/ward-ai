package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One pass over a Kotlin file, read from its syntax tree only. The header gives the package and the
 * imports. Top-level declarations are qualified by the package and nested ones by their type; a
 * file with top-level functions or properties declares its facade class. Name references come from
 * the rest of the tree, see {@link KotlinReferences}; no types are inferred.
 */
final class KotlinFileScan {
  private static final String PREAMBLE = "preamble";
  private static final String PACKAGE_HEADER = "packageHeader";
  private static final String IMPORT_LIST = "importList";
  private static final String IMPORT_HEADER = "importHeader";
  private static final String IMPORT_ALIAS = "importAlias";
  private static final String TOP_LEVEL = "topLevelObject";
  private static final String SIMPLE_IDENTIFIER = "simpleIdentifier";
  private static final String IDENTIFIER = "identifier";
  private static final String VARIABLE = "variableDeclaration";
  private static final String FUN_DECLARATION = "functionDeclaration";
  private static final String CLASS_BODY = "classBody";
  private static final String ENUM_CLASS_BODY = "enumClassBody";
  private static final String CLASS_MEMBER = "classMemberDeclaration";
  private static final String COMPANION = "Companion";
  private static final String STAR = "*";
  private static final Set<String> TYPE_DECLARATIONS =
      Set.of("classDeclaration", "objectDeclaration", "companionObject", "typeAlias");
  private static final Set<String> MEMBER_DECLARATIONS =
      Set.of("functionDeclaration", "propertyDeclaration");

  private final String path;
  private final List<Symbol> declared = new ArrayList<>();
  private final List<Reference> references = new ArrayList<>();
  private final KotlinReferences referencesOf = new KotlinReferences(references);
  private boolean hasTopLevelMembers;

  KotlinFileScan(final String path) {
    this.path = path;
  }

  /** Summarizes the file under {@code root}. */
  FileSummary scan(final SyntaxNode root) {
    final String packageName = readHeader(root);
    for (final SyntaxNode object : KotlinSyntax.children(root, TOP_LEVEL)) {
      object.children().forEach(decl -> declare(decl, packageName, true));
    }
    if (hasTopLevelMembers) {
      final String facade = KotlinFacade.nameOf(root, path);
      declared.add(new Symbol(JavaNames.qualify(packageName, facade), root.span()));
    }
    return new FileSummary(path, declared, references, declared);
  }

  /** Reads the package and the imports from the preamble, and returns the package name. */
  private String readHeader(final SyntaxNode root) {
    String packageName = "";
    for (final SyntaxNode preamble : KotlinSyntax.children(root, PREAMBLE)) {
      for (final SyntaxNode header : KotlinSyntax.children(preamble, PACKAGE_HEADER)) {
        final List<SyntaxNode> names = KotlinSyntax.children(header, IDENTIFIER);
        if (!names.isEmpty()) {
          packageName = KotlinSyntax.segments(names.get(0));
          references.add(new Reference("package:" + packageName, header.span()));
        }
      }
      for (final SyntaxNode list : KotlinSyntax.children(preamble, IMPORT_LIST)) {
        KotlinSyntax.children(list, IMPORT_HEADER).forEach(this::importReferences);
      }
    }
    return packageName;
  }

  /**
   * An import names a declaration; a star import names a package; an alias also names the alias.
   */
  private void importReferences(final SyntaxNode header) {
    final String name = KotlinSyntax.segments(KotlinSyntax.children(header, IDENTIFIER).get(0));
    final boolean onDemand = JavaSyntax.hasTerminal(header, STAR);
    if (onDemand) {
      references.add(new Reference("on-demand:" + name, header.span()));
      return;
    }
    references.add(new Reference("import:" + name, header.span()));
    for (final SyntaxNode alias : KotlinSyntax.children(header, IMPORT_ALIAS)) {
      final String simple = JavaSyntax.dotted(JavaSyntax.child(alias, SIMPLE_IDENTIFIER));
      references.add(new Reference("alias:" + simple + "=" + name, header.span()));
    }
  }

  /**
   * Declares a declaration under {@code owner} and collects the references of the rest of it. Only
   * the members of a class or object are declared; declarations inside function bodies are not.
   */
  private void declare(final SyntaxNode node, final String owner, final boolean topLevel) {
    if (TYPE_DECLARATIONS.contains(node.kind())) {
      declareType(node, owner);
    } else if (MEMBER_DECLARATIONS.contains(node.kind())) {
      declareMember(node, owner, topLevel);
    } else {
      referencesOf.visit(node);
    }
  }

  private void declareType(final SyntaxNode node, final String owner) {
    final String qualified = JavaNames.qualify(owner, declaredTypeName(node));
    declared.add(new Symbol(qualified, node.span()));
    for (final SyntaxNode child : node.children()) {
      if (CLASS_BODY.equals(child.kind()) || ENUM_CLASS_BODY.equals(child.kind())) {
        declareMembers(child, qualified);
      } else {
        referencesOf.visit(child);
      }
    }
  }

  /** Declares the class members of a body, which hold the nested types. */
  private void declareMembers(final SyntaxNode body, final String owner) {
    for (final SyntaxNode child : body.children()) {
      if (CLASS_MEMBER.equals(child.kind())) {
        child.children().forEach(member -> declare(member, owner, false));
      } else {
        referencesOf.visit(child);
      }
    }
  }

  /** A top-level function or property is declared by its package; a member is not declared. */
  private void declareMember(final SyntaxNode node, final String owner, final boolean topLevel) {
    if (topLevel) {
      memberName(node)
          .ifPresent(
              name -> {
                declared.add(new Symbol(JavaNames.qualify(owner, name), node.span()));
                hasTopLevelMembers = true;
              });
    }
    for (final SyntaxNode child : node.children()) {
      referencesOf.visit(child);
    }
  }

  /** The name of a class, object, companion or type alias. */
  private static String declaredTypeName(final SyntaxNode node) {
    if (node.children().stream().noneMatch(child -> SIMPLE_IDENTIFIER.equals(child.kind()))) {
      return COMPANION;
    }
    return JavaSyntax.dotted(JavaSyntax.child(node, SIMPLE_IDENTIFIER));
  }

  private static Optional<String> memberName(final SyntaxNode node) {
    if (FUN_DECLARATION.equals(node.kind())) {
      return KotlinSyntax.children(node, IDENTIFIER).stream()
          .map(KotlinSyntax::segments)
          .findFirst();
    }
    return KotlinSyntax.children(node, VARIABLE).stream()
        .map(variable -> JavaSyntax.dotted(JavaSyntax.child(variable, SIMPLE_IDENTIFIER)))
        .findFirst();
  }
}
