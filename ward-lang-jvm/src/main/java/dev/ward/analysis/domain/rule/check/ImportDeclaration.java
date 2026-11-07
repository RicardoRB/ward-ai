package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One import declaration of a unit. The name is written without the trailing star, which is kept in
 * {@code star}; the parent is the name without its last segment.
 *
 * @param name the imported name, such as {@code java.util.List}, or {@code java.util} for a star
 * @param isStatic whether the declaration is {@code import static}
 * @param isModule whether the declaration is {@code import module}
 * @param star whether the declaration ends in {@code .*}
 * @param span the span of the whole declaration
 */
record ImportDeclaration(String name, boolean isStatic, boolean isModule, boolean star, Span span) {

  private static final String IMPORT_KIND = "importDeclaration";
  private static final String NAME_KIND = "qualifiedName";
  private static final String PACKAGE_KIND = "packageDeclaration";
  private static final String ANNOTATION_KIND = "annotation";
  private static final String STATIC = "static";
  private static final String MODULE = "module";
  private static final String STAR = "*";
  private static final String DOT = ".";
  private static final String STAR_SUFFIX = ".*";

  /** Returns the type and static imports of {@code root} in source order. */
  static List<ImportDeclaration> declarations(final SyntaxNode root) {
    return all(root).stream().filter(declaration -> !declaration.isModule()).toList();
  }

  /** Returns the module imports of {@code root} in source order. */
  static List<ImportDeclaration> modules(final SyntaxNode root) {
    return all(root).stream().filter(ImportDeclaration::isModule).toList();
  }

  /** Returns the package the unit declares, if it declares one. */
  static Optional<String> packageOf(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(PACKAGE_KIND)).stream()
        .findFirst()
        .flatMap(
            visit -> SyntaxWalk.firstDescendant(visit.node(), NAME_KIND, Set.of(ANNOTATION_KIND)))
        .map(SyntaxWalk::text);
  }

  /** Returns the name as written, with the star when there is one. */
  String fullName() {
    return star ? name + STAR_SUFFIX : name;
  }

  /** Returns the name without its last segment; for a star import, the name itself. */
  String classPart() {
    return star ? name : parent();
  }

  /** Returns the name without its last segment, or the empty text for a single segment. */
  String parent() {
    final int dot = name.lastIndexOf(DOT);
    return dot < 0 ? "" : name.substring(0, dot);
  }

  static List<ImportDeclaration> all(final SyntaxNode root) {
    final List<ImportDeclaration> declarations = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(IMPORT_KIND))) {
      parse(visit.node()).ifPresent(declarations::add);
    }
    return List.copyOf(declarations);
  }

  private static Optional<ImportDeclaration> parse(final SyntaxNode node) {
    final Optional<SyntaxNode> name =
        node.children().stream().filter(child -> NAME_KIND.equals(child.kind())).findFirst();
    if (name.isEmpty()) {
      return Optional.empty();
    }
    final int nameStart = name.get().span().startOffset();
    final int nameEnd = name.get().span().endOffset();
    final List<SyntaxNode> leaves = SyntaxWalk.leaves(node);
    final boolean isStatic = leaves.stream().anyMatch(leaf -> isKeyword(leaf, nameStart, STATIC));
    final boolean isModule = leaves.stream().anyMatch(leaf -> isKeyword(leaf, nameStart, MODULE));
    final boolean star =
        leaves.stream()
            .anyMatch(
                leaf ->
                    leaf.span().startOffset() >= nameEnd && STAR.equals(leaf.fields().get("text")));
    return Optional.of(
        new ImportDeclaration(SyntaxWalk.text(name.get()), isStatic, isModule, star, node.span()));
  }

  /** A keyword is a leaf before the name, so a package called {@code module} is not one. */
  private static boolean isKeyword(final SyntaxNode leaf, final int nameStart, final String word) {
    return leaf.span().startOffset() < nameStart && word.equals(leaf.fields().get("text"));
  }
}
