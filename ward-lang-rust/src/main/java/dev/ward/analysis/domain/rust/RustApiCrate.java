package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiVisibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Applies Rust's visibility to what the files declare: an item is API when it is {@code pub} and
 * every module on its path is {@code pub} down from a crate root; a {@code pub use} of a known item
 * adds it under the imported name.
 */
final class RustApiCrate {
  private static final String TOP_LEVEL = "<top-level>";
  private static final String SEPARATOR = ".";
  private static final String CRATE = "crate";
  private static final String SELF = "self";
  private static final String SUPER = "super";

  private final RustApiDecls decls;
  private final Set<String> roots;
  private final Map<String, Boolean> modules = new HashMap<>();

  RustApiCrate(final RustApiDecls decls, final Set<String> roots) {
    this.decls = decls;
    this.roots = Set.copyOf(roots);
    decls.modules().forEach(
        module -> modules.merge(module.path(), module.pub(), Boolean::logicalOr)
    );
  }

  /** Returns the types of the API, with the free functions of each module as one type. */
  List<ApiType> types() {
    final Map<String, ApiType> found = new TreeMap<>();
    final Map<String, List<ApiMember>> tops = new TreeMap<>();
    final Map<String, RustApiDecls.Item> all = new HashMap<>();
    decls.items().forEach(item -> all.put(item.module() + SEPARATOR + item.type().name(), item));
    decls.items().stream()
        .filter(item -> item.pub() && exposed(item.module()))
        .forEach(
            item -> found.putIfAbsent(name(item.module(), item.type().name()), withImpls(item))
        );
    decls.tops().stream()
        .filter(top -> top.pub() && exposed(top.module()))
        .forEach(
            top -> tops.computeIfAbsent(top.module(), key -> new ArrayList<>()).add(top.member())
        );
    decls.uses().stream()
        .filter(use -> exposed(use.module()))
        .forEach(use -> reexport(use, all, found, tops));
    tops.forEach(
        (module, members) -> found.put(name(module, TOP_LEVEL), topLevel(module, members))
    );
    return found.values().stream().sorted(Comparator.comparing(ApiType::name)).toList();
  }

  private boolean exposed(final String module) {
    if (roots.contains(module)) {
      return true;
    }
    final int dot = module.lastIndexOf(SEPARATOR);
    return dot > 0 && modules.getOrDefault(module, false) && exposed(module.substring(0, dot));
  }

  private ApiType withImpls(final RustApiDecls.Item item) {
    final List<ApiMember> members = new ArrayList<>(item.type().members());
    decls.impls().stream()
        .filter(
            impl -> impl.module().equals(item.module()) && impl.type().equals(item.type().name())
        )
        .forEach(
            impl ->
                impl.members().stream()
                    .filter(member -> member.visibility() == ApiVisibility.PUBLIC)
                    .forEach(members::add)
        );
    return copy(item.type(), name(item.module(), item.type().name()), members);
  }

  private void reexport(
      final RustApiDecls.Use use,
      final Map<String,
      RustApiDecls.Item> all,
      final Map<String,
      ApiType> found,
      final Map<String,
      List<ApiMember>> tops
  ) {
    final String target = resolve(use.module(), use.target());
    final RustApiDecls.Item item = all.get(target);
    if (item != null) {
      found.putIfAbsent(name(use.module(), use.alias()), withImplsAs(item, use));
      return;
    }
    final int dot = target.lastIndexOf(SEPARATOR);
    if (dot > 0) {
      decls.tops().stream()
          .filter(top -> top.module().equals(target.substring(0, dot)))
          .filter(top -> top.member().name().equals(target.substring(dot + 1)))
          .forEach(
              top ->
                  tops.computeIfAbsent(use.module(), key -> new ArrayList<>())
                      .add(RustApiMembers.renamed(top.member(), use.alias()))
          );
    }
  }

  private ApiType withImplsAs(final RustApiDecls.Item item, final RustApiDecls.Use use) {
    return copy(withImpls(item), name(use.module(), use.alias()), withImpls(item).members());
  }

  private static String resolve(final String module, final List<String> path) {
    String base = module;
    int index = 0;
    final String first = path.getFirst();
    if (CRATE.equals(first)) {
      base = module.substring(
          0,
          module.indexOf(SEPARATOR) < 0 ? module.length() : module.indexOf(SEPARATOR)
      );
      index = 1;
    } while (index < path.size() && (SELF.equals(path.get(index)) || SUPER.equals(
        path.get(index)
    ))) {
      base = SUPER.equals(path.get(index)) ? parent(base) : base;
      index++;
    }
    return base + SEPARATOR + String.join(SEPARATOR, path.subList(index, path.size()));
  }

  private static String parent(final String module) {
    return module.contains(SEPARATOR) ? module.substring(0, module.lastIndexOf(SEPARATOR)) : module;
  }

  private static String name(final String module, final String simple) {
    return module + SEPARATOR + simple;
  }

  private static ApiType topLevel(final String module, final List<ApiMember> members) {
    return new ApiType(
        name(module, TOP_LEVEL),
        ApiType.Kind.OBJECT,
        ApiVisibility.PUBLIC,
        true,
        false,
        false,
        List.of(),
        List.of(),
        members.stream().sorted(Comparator.comparing(ApiMember::id)).toList(),
        members.getFirst().location()
    );
  }

  private static ApiType copy(
      final ApiType type,
      final String name,
      final List<ApiMember> members
  ) {
    return new ApiType(
        name,
        type.kind(),
        type.visibility(),
        type.isFinal(),
        type.isSealed(),
        type.isAbstract(),
        type.typeParameters(),
        type.supertypes(),
        members,
        type.location()
    );
  }
}
