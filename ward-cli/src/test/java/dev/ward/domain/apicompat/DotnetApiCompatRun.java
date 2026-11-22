package dev.ward.domain.apicompat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Runs the Microsoft.DotNet.ApiCompat of the .NET SDK: packs the baseline and the revision of a
 * fixture as libraries and packs the revision with package validation against the baseline package,
 * which runs ApiCompat and reports its {@code CPxxxx} errors.
 */
final class DotnetApiCompatRun {
  private static final String PROJECT =
      """
      <Project Sdk="Microsoft.NET.Sdk">
        <PropertyGroup>
          <TargetFramework>net9.0</TargetFramework>
          <PackageId>Lib</PackageId>
          <Version>1.0.0</Version>
          <Nullable>disable</Nullable>
          <ImplicitUsings>disable</ImplicitUsings>
          <NoWarn>CS8981;CS0169;CS0649;CS1591;NU5128;NU5039</NoWarn>
        </PropertyGroup>
      </Project>
      """;
  private static final String TYPE = "Type";
  private static final int KIND_GROUP = 2;
  private static final int SIGNATURE_GROUP = 3;
  private static final Pattern ERROR = Pattern.compile("error (CP\\d+): (Type|Member) '([^']+)'");
  private static final int MINUTES = 5;
  private static final Map<String, String> ACCESSORS =
      Map.of("get", "get_", "set", "set_", "init", "set_", "add", "add_", "remove", "remove_");

  private final String dotnet;

  DotnetApiCompatRun(final String dotnet) {
    this.dotnet = dotnet;
  }

  /** Returns the types and members ApiCompat reports, as {@code Type} or {@code Type#member}. */
  Set<String> changes(final Path baseline, final Path revision, final Path work)
      throws IOException, InterruptedException {
    final Path old = work.resolve("old");
    final Path current = work.resolve("new");
    project(baseline, old);
    project(revision, current);
    run(old, List.of("pack", "--disable-build-servers", "-o", "out"));
    final String output =
        run(
            current,
            List.of(
                "pack",
                "--disable-build-servers",
                "-p:EnablePackageValidation=true",
                "-p:PackageValidationBaselinePath=" + old.resolve("out/Lib.1.0.0.nupkg"),
                "-o",
                "out"
            )
        );
    final Set<String> found = new TreeSet<>();
    final Matcher matcher = ERROR.matcher(output);
    while (matcher.find()) {
      found.add(key(matcher.group(KIND_GROUP), matcher.group(SIGNATURE_GROUP)));
    }
    return found;
  }

  private static void project(final Path sources, final Path target) throws IOException {
    Files.createDirectories(target);
    Files.writeString(target.resolve("Lib.csproj"), PROJECT, StandardCharsets.UTF_8);
    try (Stream<Path> files = Files.walk(sources)) {
      for (final Path file : files.filter(Files::isRegularFile).toList()) {
        Files.copy(
            file,
            target.resolve(sources.relativize(file).toString()),
            StandardCopyOption.REPLACE_EXISTING
        );
      }
    }
  }

  private String run(final Path directory, final List<String> arguments)
      throws IOException, InterruptedException {
    final List<String> command = new java.util.ArrayList<>(List.of(dotnet));
    command.addAll(arguments);
    final Process process =
        new ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .start();
    final String output = new String(
        process.getInputStream().readAllBytes(),
        StandardCharsets.UTF_8
    );
    if (!process.waitFor(MINUTES, TimeUnit.MINUTES)) {
      process.destroyForcibly();
    }
    return output;
  }

  /** Maps {@code Member 'int P.A.F(int)'} to {@code P.A#F} and a type to its name. */
  static String key(final String kind, final String signature) {
    if (TYPE.equals(kind)) {
      return generic(signature);
    }
    final String name = signature.contains("(") ? signature.substring(
        0,
        signature.indexOf('(')
    ) : signature;
    final String qualified = name.substring(name.lastIndexOf(' ') + 1);
    final int dot = qualified.lastIndexOf('.');
    final String owner = qualified.substring(0, dot);
    final String member = qualified.substring(dot + 1);
    if (ACCESSORS.containsKey(member)) {
      final int property = owner.lastIndexOf('.');
      return generic(owner.substring(0, property))
          + "#"
          + ACCESSORS.get(member)
          + owner.substring(property + 1);
    }
    final String type = generic(owner);
    return type + "#" + (type.endsWith(member) ? "<init>" : member);
  }

  /** Writes {@code A<T, U>} as {@code A`2}, as the metadata name does. */
  private static String generic(final String name) {
    final int open = name.indexOf('<');
    if (open < 0) {
      return name;
    }
    final String arguments = name.substring(open + 1, name.lastIndexOf('>'));
    return name.substring(0, open) + "`" + arguments.split(",").length;
  }
}
