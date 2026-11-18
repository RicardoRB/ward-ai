import Foundation

func listFixed() throws {
  let process = Process()
  process.executableURL = URL(fileURLWithPath: "/bin/ls")
  process.arguments = ["-l", "/tmp"]
  try process.run()
}

func countLines(_ url: URL) throws {
  let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
  let raw = components?.queryItems?.first?.value ?? ""
  let count = Int(raw) ?? 10
  let process = Process()
  process.executableURL = URL(fileURLWithPath: "/usr/bin/head")
  process.arguments = ["-n", "\(count)", "/var/log/system.log"]
  try process.run()
}

func notAProcess(_ url: URL) {
  let query = url.query ?? ""
  print(query)
  let task = Task()
  task.arguments = [query]
}
