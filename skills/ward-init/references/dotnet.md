# C# projects (.NET SDK)

Use this reference when `ward detect` reports `CSHARP` with `DOTNET`. Everything here is read-only
until the user approves the plan.

## Inspection

- **Project kind.** A `.sln` or `.slnx` file, or a `.csproj`, makes a C# project. A solution owns the
  projects it lists, so a solution of several projects is one Ward project built from its root.
- **Toolchain.** Init reads build files and never runs `dotnet`. `ward doctor` reports a missing
  `dotnet` and links https://dotnet.microsoft.com/download; tell the user to install the .NET SDK.

## Choices

- **Gates.** `dotnet-build` and `dotnet-test`. `strict` adds `-warnaserror`.
- **Files.** No `.csproj`, solution or props file is edited. A directory with several solutions, or
  several projects and no solution, needs the commands edited to name the target.
