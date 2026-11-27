package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Decides whether a name that holds an array or an object is sealed: every use of it, and of each
 * name it is copied to, only reads it. A sealed name still holds what its declaration gave it, so
 * the values of the declaration are the values of every read.
 *
 * <p>A use reads when it takes a member and the member is a string, a number or a boolean, loops
 * over the value, hands it to a function that is itself sealed, stores it in a literal or another
 * sealed name, or calls a function that the data holds. Assigning to a member, calling a method
 * that is not known to read, or sending the value anywhere else is not a read.
 *
 * <p>shortcut: only the file of the name and the files its sink depends on are read, so a file
 * that imports an exported constant and is not among them can change it unseen; and a function
 * stored in the data could change the data through {@code this}. Upgrade with a whole-project
 * reference index.
 */
final class NodeProgramSeal {
  private static final String SHORTHAND = "identifierOrKeyWord";
  private static final String PROPERTY = "propertyAssignment";
  private static final int MAX_DEPTH = 24;
  private static final int NAME_LEVELS = 4;

  private final NodeProgramValues values;
  private final NodeProgramUses uses;
  private final Map<NodeProgramFile, Set<String>> solved = new IdentityHashMap<>();
  private final Deque<NodeProgramFile> solving = new ArrayDeque<>();

  NodeProgramSeal(final NodeProgramValues values) {
    this.values = values;
    this.uses = new NodeProgramUses(values, this);
  }

  NodeProgramUses uses() {
    return uses;
  }

  /**
   * Whether no use of the binding in its file can change what it holds. A file is solved as a
   * whole: every binding starts as sealed, and each one that has a use that is not a read is
   * dropped, until no binding changes, which keeps the largest consistent set.
   */
  boolean sealed(final NodeProgramFile file, final Binding binding) {
    Set<String> sealedKeys = solved.get(file);
    if (sealedKeys == null) {
      sealedKeys = solve(file);
    } else if (solving.contains(file) && solving.peek() != file) {
      return false;
    }
    return sealedKeys.contains(key(binding));
  }

  private Set<String> solve(final NodeProgramFile file) {
    final List<Binding> bindings = file.scopes().bindings();
    final Set<String> assumed = new HashSet<>();
    bindings.forEach(binding -> assumed.add(key(binding)));
    solved.put(file, assumed);
    solving.push(file);
    boolean changed = true;
    while (changed) {
      changed = false;
      for (final Binding binding : bindings) {
        if (assumed.contains(key(binding)) && !usesRead(file, binding)) {
          assumed.remove(key(binding));
          changed = true;
        }
      }
    }
    solving.pop();
    return assumed;
  }

  private static String key(final Binding binding) {
    return binding.declaration().startOffset() + ":" + binding.name();
  }

  private boolean usesRead(final NodeProgramFile file, final Binding binding) {
    return file.scopes().usesOf(binding).stream().allMatch(use -> reads(file, binding, use));
  }

  private boolean reads(
      final NodeProgramFile file,
      final Binding binding,
      final NameReference use
  ) {
    if (use.span().startOffset() == binding.declaration().startOffset()) {
      return true;
    }
    if (use.access() != ReferenceAccess.READ) {
      return false;
    }
    return file.nameAt(use.span())
        .flatMap(token -> holder(file, token))
        .map(holder -> consumed(file, holder, 0))
        .orElse(false);
  }

  /** The expression that is only the name, or the shorthand property that writes it. */
  private static Optional<SyntaxNode> holder(final NodeProgramFile file, final SyntaxNode token) {
    Optional<SyntaxNode> node = file.parent(token);
    for (int level = 0; level < NAME_LEVELS && node.isPresent(); level++) {
      final SyntaxNode current = node.get();
      if (NodeProgramShapes.name(current).filter(name -> name == token).isPresent()) {
        return node;
      }
      if (SHORTHAND.equals(current.kind())) {
        return node;
      }
      node = file.parent(current);
    }
    return Optional.empty();
  }

  private boolean consumed(final NodeProgramFile file, final SyntaxNode current, final int depth) {
    final Optional<SyntaxNode> parent = file.parent(current);
    if (depth > MAX_DEPTH || parent.isEmpty() || !values.spend()) {
      return false;
    }
    final NodeProgramRoles.Role role = NodeProgramRoles.roleOf(parent.get(), current);
    return role != NodeProgramRoles.Role.MUTATION
        && (primitive(file, current) || followed(file, current, parent.get(), role, depth));
  }

  private boolean followed(
      final NodeProgramFile file,
      final SyntaxNode current,
      final SyntaxNode parent,
      final NodeProgramRoles.Role role,
      final int depth
  ) {
    final Predicate<SyntaxNode> onward = node -> consumed(file, node, depth + 1);
    return switch (role) {
      case CONSUMED -> true;
      case PASS, MEMBER -> onward.test(parent);
      case CALLEE -> uses.called(file, current, parent, onward);
      case ARGUMENT -> uses.argument(file, parent);
      case INITIALIZER -> uses.declared(file, parent);
      case ITERABLE -> uses.loopBound(file, parent);
      case ELEMENT -> uses.literal(file, parent, onward);
      default -> false;
    };
  }

  /** Whether every value of the expression is a string, a number or a boolean. */
  private boolean primitive(final NodeProgramFile file, final SyntaxNode expression) {
    final NodeProgramValues scratch = values.scratch();
    final Optional<List<NodeProgramValues.Term>> terms = scratch.of(file, expression, 0);
    return scratch.reentries() == 0
        && terms.isPresent()
        && terms.get().stream().noneMatch(NodeProgramObjects::structure);
  }
}
