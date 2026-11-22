package dev.ward.infrastructure.license.dotnet;

import static dev.ward.infrastructure.license.LicenseReaderFixtures.dependency;
import static dev.ward.infrastructure.license.LicenseReaderFixtures.project;
import static dev.ward.infrastructure.license.LicenseReaderFixtures.write;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.license.LicenseLookup;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NugetLicenseMetadataTest {
  private static final String NUSPEC = "newtonsoft.json/13.0.3/newtonsoft.json.nuspec";

  @TempDir Path root;

  private LicenseLookup lookup() {
    return new NugetLicenseMetadata(root.resolve("packages"))
        .lookup(
            project(root, Language.CSHARP, BuildTool.DOTNET),
            "",
            dependency(Ecosystem.NUGET, "Newtonsoft.Json", "13.0.3")
        );
  }

  private void nuspec(final String metadata) throws IOException {
    write(
        root,
        "packages/" + NUSPEC,
        "<?xml version=\"1.0\"?><package xmlns=\"http://schemas.microsoft.com/packaging/2013/05/nuspec.xsd\">"
            + "<metadata><id>Newtonsoft.Json</id>"
            + metadata
            + "</metadata></package>"
    );
  }

  @Test
  void readsTheSpdxExpression() throws IOException {
    nuspec("<license type=\"expression\">MIT</license>");

    final LicenseLookup result = lookup();

    assertThat(result.license().describe()).isEqualTo("MIT");
    assertThat(result.detail()).isEqualTo("nuspec");
  }

  @Test
  void readsACompoundExpression() throws IOException {
    nuspec("<license type=\"expression\">MIT OR Apache-2.0</license>");

    assertThat(lookup().license().describe()).isEqualTo("MIT OR Apache-2.0");
  }

  @Test
  void aLicenseFileIsUnrecognizedAndNamesTheFile() throws IOException {
    nuspec("<license type=\"file\">LICENSE.md</license>");

    final LicenseLookup result = lookup();

    assertThat(result.reason()).isEqualTo(LicenseLookup.Reason.UNRECOGNIZED);
    assertThat(result.detail()).contains("LICENSE.md");
  }

  @Test
  void aLicenseUrlIsNotGuessed() throws IOException {
    nuspec("<licenseUrl>https://example.com/license</licenseUrl>");

    assertThat(lookup().reason()).isEqualTo(LicenseLookup.Reason.NO_LICENSE_DECLARED);
  }

  @Test
  void aNuspecWithoutALicenseDeclaresNone() throws IOException {
    nuspec("<description>x</description>");

    assertThat(lookup().reason()).isEqualTo(LicenseLookup.Reason.NO_LICENSE_DECLARED);
  }

  @Test
  void aMissingPackageIsNotInstalled() {
    assertThat(lookup().reason()).isEqualTo(LicenseLookup.Reason.NOT_INSTALLED);
  }
}
