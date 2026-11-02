package dev.ward.infrastructure.init.swift;

import dev.ward.domain.init.ArchitectureGates;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders the files of Ward's Harmonize architecture rules: the package manifest, the rules test
 * for an architecture style, and the root {@code .harmonize.yaml} that marks the project root.
 */
final class HarmonizeRulesSource {
  /** The ownership marker every file Ward writes for Harmonize starts with. */
  static final String MARKER = "managed-by: ward";

  /** The rules file, relative to the project root. */
  static final String RULES =
      ArchitectureGates.HARMONIZE_PACKAGE + "/Tests/ArchitectureRules/LayerRules.swift";

  /** The package manifest, relative to the project root. */
  static final String MANIFEST = ArchitectureGates.HARMONIZE_PACKAGE + "/Package.swift";

  /** The root config Harmonize needs to find the project root. */
  static final String ROOT_CONFIG = ".harmonize.yaml";

  private static final List<String> EXCLUDES =
      List.of(".ward", ".build", "Pods", "Carthage", "DerivedData");

  private static final List<String> OUTER = List.of("adapter", "infrastructure");

  /**
   * The rule shared by every layer test. A file belongs to a layer when one of its folders below
   * the project root is named after the layer, ignoring case. The root is five path components
   * above this file.
   */
  private static final String BODY =
      """
      import Foundation
      import Harmonize
      import Testing

      /// Code under the source folder must not import, or name a type declared under, a forbidden folder.
      private struct LayerRule {
          let source: String
          let forbidden: Set<String>

          private static let rootCount = URL(fileURLWithPath: #filePath).pathComponents.count - 5

          private static func folders(of file: SwiftSourceCode) -> Set<String> {
              guard let parts = file.filePath?.pathComponents, parts.count > rootCount else { return [] }
              return Set(parts.dropFirst(rootCount).dropLast().map { $0.lowercased() })
          }

          private static func declaredTypes(_ file: SwiftSourceCode) -> [String] {
              file.classes(includeNested: true).map(\\.name)
                  + file.structs(includeNested: true).map(\\.name)
                  + file.enums(includeNested: true).map(\\.name)
                  + file.protocols(includeNested: true).map(\\.name)
                  + file.actors(includeNested: true).map(\\.name)
          }

          private static func typeTexts(_ file: SwiftSourceCode) -> [String] {
              file.classes(includeNested: true).flatMap(\\.inheritanceTypesNames)
                  + file.structs(includeNested: true).flatMap(\\.inheritanceTypesNames)
                  + file.enums(includeNested: true).flatMap(\\.inheritanceTypesNames)
                  + file.protocols(includeNested: true).flatMap(\\.inheritanceTypesNames)
                  + file.actors(includeNested: true).flatMap(\\.inheritanceTypesNames)
                  + file.variables(includeNested: true).compactMap { $0.typeAnnotation?.annotation }
                  + file.functions(includeNested: true).flatMap { function in
                      function.parameters.compactMap { $0.typeAnnotation?.annotation }
                          + [function.returnType?.annotation].compactMap { $0 }
                  }
                  + file.initializers().flatMap { initializer in
                      initializer.parameters.compactMap { $0.typeAnnotation?.annotation }
                  }
          }

          private static func identifiers(_ text: String) -> [String] {
              text.split { !($0.isLetter || $0.isNumber || $0 == "_") }.map(String.init)
          }

          private func violations(_ file: SwiftSourceCode, _ types: Set<String>) -> [String] {
              let imported = file.imports().map(\\.name).filter { forbidden.contains($0.lowercased()) }
              let named = Set(LayerRule.typeTexts(file).flatMap(LayerRule.identifiers)).intersection(types)
              return imported.map { "imports " + $0 } + named.sorted().map { "uses " + $0 }
          }

          func check() {
              let types = Set(
                  Harmonize.productionCode().sources()
                      .filter { !LayerRule.folders(of: $0).isDisjoint(with: forbidden) }
                      .flatMap(LayerRule.declaredTypes))
              let layer = Harmonize.productionCode().sources()
                  .filter { LayerRule.folders(of: $0).contains(source) }
              for file in layer {
                  let found = violations(file, types)
                  [file].assertTrue(
                      message: "Layer " + source + " must not depend on " + forbidden.sorted().joined(separator: ", ")
                          + ": " + found.joined(separator: ", ")
                  ) { _ in found.isEmpty }
              }
          }
      }
      """;

  /** One source layer and the layers its code must not depend on. */
  record SourceLayer(String name, List<String> forbidden) {
    /** Copies the forbidden list so the layer stays immutable. */
    SourceLayer {
      forbidden = List.copyOf(forbidden);
    }
  }

  private HarmonizeRulesSource() {}

  /** Returns the source layers of {@code style}; an unknown style uses the layered rules. */
  static List<SourceLayer> layers(final String style) {
    return switch (style) {
      case "hexagonal" ->
          List.of(new SourceLayer("domain", OUTER), new SourceLayer("application", OUTER));
      case "clean" ->
          List.of(
              new SourceLayer("domain", List.of("controller", "adapter", "infrastructure")),
              new SourceLayer("core", List.of("controller", "adapter", "infrastructure")));
      default -> List.of(new SourceLayer("controller", List.of("repository")));
    };
  }

  /** Returns the manifest that pins Harmonize exactly, with Ward's marker. */
  static String manifest() {
    return "// swift-tools-version: 5.9\n"
        + "// "
        + MARKER
        + "\n"
        + "import PackageDescription\n"
        + "\n"
        + "let package = Package(\n"
        + "    name: \"WardArchitectureRules\",\n"
        + "    platforms: [.macOS(.v13)],\n"
        + "    dependencies: [\n"
        + "        .package(\n"
        + "            url: \""
        + SwiftToolCatalog.HARMONIZE_URL
        + "\",\n"
        + "            exact: \""
        + SwiftToolCatalog.HARMONIZE_VERSION
        + "\"\n"
        + "        ),\n"
        + "    ],\n"
        + "    targets: [\n"
        + "        .testTarget(\n"
        + "            name: \"ArchitectureRules\",\n"
        + "            dependencies: [.product(name: \"Harmonize\", package: \"Harmonize\")]\n"
        + "        ),\n"
        + "    ]\n"
        + ")\n";
  }

  /** Returns the rules test for {@code style}: one Swift Testing test per source layer. */
  static String rules(final String style) {
    final String tests =
        layers(style).stream()
            .map(
                layer ->
                    "@Test\nfunc "
                        + layer.name()
                        + "LayerRespectsRules() {\n"
                        + "    LayerRule(source: \""
                        + layer.name()
                        + "\", forbidden: "
                        + layer.forbidden().stream()
                            .map(name -> "\"" + name + "\"")
                            .collect(Collectors.joining(", ", "[", "]"))
                        + ").check()\n}\n")
            .collect(Collectors.joining("\n"));
    return "// "
        + MARKER
        + "\n"
        + "// Architecture rules for the "
        + style
        + " style. Ward generates this file; change the style with `ward init --architecture`.\n"
        + BODY
        + "\n"
        + tests;
  }

  /** Returns the root config with Ward's marker and the excludes of Ward's own package. */
  static String rootConfig() {
    return "# "
        + MARKER
        + "\n"
        + "excludes:\n"
        + EXCLUDES.stream().map(name -> "  - " + name + "\n").collect(Collectors.joining());
  }
}
