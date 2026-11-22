# C# projects (.NET SDK)

Use this reference when `ward detect` reports `CSHARP` with `DOTNET`. Everything here is read-only
until the user approves the plan.

## Inspection

- **Project kind.** A `.sln` or `.slnx` file, or a `.csproj`, makes a C# project. A solution owns the
  projects it lists, so a solution of several projects is one Ward project built from its root.
- **Toolchain.** Init reads build files and never runs `dotnet`. `ward doctor` reports a missing
  `dotnet` and links https://dotnet.microsoft.com/download; tell the user to install the .NET SDK.

## Choices

- **Gates.** `dotnet-build`, `dotnet-test` (writes a Coverlet Cobertura report; the test project
  needs `coverlet.collector`, which the xUnit, NUnit and MSTest templates include) and `coverage`
  (80%; strict 85% and 75% branches). `strict` adds `-warnaserror` and `stryker-net` (60% minimum,
  `ci` profile), which needs `.config/dotnet-tools.json`; init writes it when absent.
- **Files.** No `.csproj`, solution or props file is edited; strict may add `.config/dotnet-tools.json`. A directory with several solutions, or
  several projects and no solution, needs the commands edited to name the target.
