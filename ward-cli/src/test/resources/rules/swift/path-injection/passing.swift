import Foundation

func readSafely(_ url: URL, base: URL) -> Data? {
  let name = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first?.value ?? ""
  let file = base.appendingPathComponent(URL(fileURLWithPath: name).lastPathComponent)
  return FileManager.default.contents(atPath: file.path)
}

func readFixed(base: URL) -> Data? {
  let file = base.appendingPathComponent("settings.json")
  return FileManager.default.contents(atPath: file.path)
}

func printOnly(_ url: URL) {
  let name = url.query ?? ""
  print(name)
}

func removeCache(_ url: URL) throws {
  let raw = url.query ?? ""
  let age = Int(raw) ?? 0
  try FileManager.default.removeItem(atPath: "/var/cache/entry-\(age)")
}
