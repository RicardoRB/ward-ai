package dev.ward.infrastructure.init.maven;

import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.QualityPreset;
import java.util.Set;
import java.util.function.Predicate;

/** Places the selected tools into an in-memory pom edit and validates what was added. */
final class PomToolEdits {
  private PomToolEdits() {}

  static void placeTools(
      final PomEdit edit,
      final Set<String> selected,
      final PomToolVersions versions,
      final QualityPreset preset,
      final Predicate<? super CompatibilityFinding> approval
  ) {
    PomTools.TOOLS.stream()
        .filter(tool -> selected.contains(tool.gate()))
        .forEach(
            tool ->
                versions
                    .unavailable(tool)
                    .ifPresentOrElse(
                        reason -> edit.warnings.add(tool.gate() + " omitted: " + reason),
                        () -> {
                          if (tool.module() && !edit.modules.isEmpty()) {
                            PomModulePlacement.place(tool, preset, versions, edit);
                          } else {
                            PomToolPlacement.apply(tool, preset, versions, approval, edit);
                          }
                        }
                    )
        );
  }

  static void validateDeclarations(
      final PomEdit edit,
      final Set<String> selected,
      final Set<String> rootGates
  ) {
    if (!edit.tools.isEmpty()) {
      PomDeclarationValidator.validate(
          edit.content,
          PomTools.addedDeclarations(edit.original, edit.content, rootGates)
      );
    }
    edit.modules.forEach(
        module -> {
          if (!module.tools.isEmpty()) {
            PomDeclarationValidator.validate(
                module.content,
                PomTools.addedDeclarations(module.original, module.content, selected)
            );
          }
        }
    );
  }
}
