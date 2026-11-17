import Foundation

func read(_ url: URL, base: URL) -> Data? {
  let name = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first?.value ?? ""
  let file = base.appendingPathComponent(name)
  return FileManager.default.contents(atPath: file.path)
}

func remove(_ req: Request) throws {
  let name = req.parameters.get("name") ?? ""
  try FileManager.default.removeItem(atPath: "/var/uploads/" + name)
}

func openHandle(_ url: URL) -> FileHandle? {
  let relative = url.query ?? ""
  let path = "/srv/files/\(relative)"
  return FileHandle(forReadingAtPath: path)
}

func load(_ url: URL) throws -> String {
  let name = URLComponents(url: url, resolvingAgainstBaseURL: false)?.query ?? ""
  return try String(contentsOfFile: "/srv/templates/" + name)
}
