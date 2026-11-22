using System.Reflection;
using Microsoft.CodeAnalysis.Diagnostics;
var ids = new SortedDictionary<string,string>();
foreach (var dir in args) {
  AppDomain.CurrentDomain.AssemblyResolve += (s, e) => {
    var p = Path.Combine(dir, new AssemblyName(e.Name).Name + ".dll");
    return File.Exists(p) ? Assembly.LoadFrom(p) : null;
  };
  foreach (var f in Directory.GetFiles(dir, "*.dll")) {
    Assembly a; try { a = Assembly.LoadFrom(f); } catch { continue; }
    Type[] types; try { types = a.GetTypes(); } catch (ReflectionTypeLoadException e) { types = e.Types.Where(t => t != null).ToArray()!; }
    foreach (var t in types) {
      if (t.IsAbstract || !typeof(DiagnosticAnalyzer).IsAssignableFrom(t)) continue;
      try {
        var an = (DiagnosticAnalyzer)Activator.CreateInstance(t, true)!;
        foreach (var d in an.SupportedDiagnostics) ids[d.Id] = d.Category;
      } catch (Exception ex) { Console.Error.WriteLine(t.Name + ": " + ex.GetType().Name); }
    }
  }
}
foreach (var kv in ids) Console.WriteLine(kv.Key + "\t" + kv.Value);
