package dev.ward.infrastructure.license.dotnet;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.license.DeclaredLicense;
import dev.ward.domain.license.LicenseLookup;
import dev.ward.domain.license.LicenseMetadata;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the {@code <license>} element of a package's {@code .nuspec} in the NuGet global-packages
 * folder ({@code <folder>/<id>/<version>/<id>.nuspec}, lower case). An SPDX expression is the
 * declared license. A license file is not read and a {@code licenseUrl} is never guessed from, so
 * both leave the package without a recognized license.
 */
public final class NugetLicenseMetadata implements LicenseMetadata {
  private static final Pattern LICENSE =
      Pattern.compile("<license\\b([^>]*)>([^<]*)</license>", Pattern.DOTALL);
  private static final Pattern TYPE = Pattern.compile("type\\s*=\\s*[\"']([^\"']*)[\"']");
  private static final String EXPRESSION = "expression";

  private final Path packages;

  /** Creates the reader over the global-packages folder {@code packages}. */
  public NugetLicenseMetadata(final Path packages) {
    this.packages = packages;
  }

  @Override
  public LicenseLookup lookup(
      final Project project,
      final String modulePath,
      final Dependency dependency
  ) {
    final String id = dependency.id().name().toLowerCase(Locale.ROOT);
    final Path nuspec =
        packages
            .resolve(id)
            .resolve(dependency.id().version().toLowerCase(Locale.ROOT))
            .resolve(id + ".nuspec");
    if (!Files.isRegularFile(nuspec)) {
      return LicenseLookup.missing(
          LicenseLookup.Reason.NOT_INSTALLED,
          dependency.id().name() + " is not in the NuGet packages folder"
      );
    }
    try {
      return declared(Files.readString(nuspec));
    } catch (IOException exception) {
      return LicenseLookup.missing(LicenseLookup.Reason.UNRECOGNIZED, "the nuspec is unreadable");
    }
  }

  private static LicenseLookup declared(final String nuspec) {
    final Matcher license = LICENSE.matcher(nuspec);
    if (!license.find()) {
      return LicenseLookup.missing(
          LicenseLookup.Reason.NO_LICENSE_DECLARED,
          "the nuspec declares no license element"
      );
    }
    final Matcher type = TYPE.matcher(license.group(1));
    final String value = license.group(2).strip();
    if (type.find() && !EXPRESSION.equals(type.group(1)) || value.isEmpty()) {
      return LicenseLookup.missing(
          LicenseLookup.Reason.UNRECOGNIZED,
          "the nuspec names a license file, " + value
      );
    }
    return LicenseLookup.found(DeclaredLicense.of(value), "nuspec");
  }
}
