package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import java.util.ArrayList;
import java.util.List;

/**
 * What the files of a Rust crate declare, before visibility and re-exports are applied.
 *
 * @param modules the declared modules
 * @param items the declared types
 * @param impls the inherent impl blocks
 * @param tops the free functions, constants and statics
 * @param uses the public imports
 */
record RustApiDecls(
    List<Module> modules,
    List<Item> items,
    List<Impl> impls,
    List<Top> tops,
    List<Use> uses
) {
  RustApiDecls {
    modules = List.copyOf(modules);
    items = List.copyOf(items);
    impls = List.copyOf(impls);
    tops = List.copyOf(tops);
    uses = List.copyOf(uses);
  }

  /** A module, by dotted path, and whether its declaration is {@code pub}. */
  record Module(String path, boolean pub) {}

  /** A type declared in {@code module}; its name is the simple name. */
  record Item(String module, boolean pub, ApiType type) {}

  /** The members of an inherent {@code impl} block of the type called {@code type}. */
  record Impl(String module, String type, List<ApiMember> members) {
    Impl {
      members = List.copyOf(members);
    }
  }

  /** A free function, constant or static of {@code module}. */
  record Top(String module, boolean pub, ApiMember member) {}

  /** A {@code use} of the path {@code target}, visible as {@code alias} in {@code module}. */
  record Use(String module, boolean pub, List<String> target, String alias) {
    Use {
      target = List.copyOf(target);
    }
  }

  /** Collects declarations while a file is read. */
  static final class Builder {
    private final List<Module> modules = new ArrayList<>();
    private final List<Item> items = new ArrayList<>();
    private final List<Impl> impls = new ArrayList<>();
    private final List<Top> tops = new ArrayList<>();
    private final List<Use> uses = new ArrayList<>();

    void module(final Module declared) {
      modules.add(declared);
    }

    void item(final Item declared) {
      items.add(declared);
    }

    void impl(final Impl declared) {
      impls.add(declared);
    }

    void top(final Top declared) {
      tops.add(declared);
    }

    void use(final Use declared) {
      uses.add(declared);
    }

    void addAll(final RustApiDecls other) {
      modules.addAll(other.modules());
      items.addAll(other.items());
      impls.addAll(other.impls());
      tops.addAll(other.tops());
      uses.addAll(other.uses());
    }

    RustApiDecls build() {
      return new RustApiDecls(modules, items, impls, tops, uses);
    }
  }
}
