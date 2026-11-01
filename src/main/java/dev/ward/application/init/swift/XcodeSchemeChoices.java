package dev.ward.application.init.swift;

import dev.ward.application.init.InitSetupCatalog;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import java.util.List;

/** The Xcode schemes the wizard can offer for a project, read through the catalog's ports. */
public final class XcodeSchemeChoices {
  private XcodeSchemeChoices() {}

  /** Returns the schemes {@code project}'s Xcode test gate can run, in listing order. */
  public static List<String> of(final InitSetupCatalog catalog, final Project project) {
    if (project.buildTool() != BuildTool.XCODE) {
      return List.of();
    }
    return catalog.inputs.swift().xcode().schemes(project.root());
  }
}
