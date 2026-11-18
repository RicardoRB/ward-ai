import Foundation

func openLink(_ url: URL) throws {
  let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
  let target = components?.queryItems?.first?.value ?? ""
  let process = Process()
  process.executableURL = URL(fileURLWithPath: "/usr/bin/env")
  process.arguments = [target]
  try process.run()
}

func runFromLink(_ url: URL) {
  let command = url.query ?? ""
  system(command)
}

func runFromRequest(_ req: Request) throws {
  let tool = req.parameters.get("tool") ?? ""
  try Process.run(URL(fileURLWithPath: tool), arguments: [])
}

func runWithOption(_ url: URL) throws {
  var option = "-l"
  if let given = URLComponents(url: url, resolvingAgainstBaseURL: false)?.query {
    option = given
  }
  let process = Process()
  process.launchPath = "/bin/ls"
  process.arguments = [option, "/tmp"]
  try process.run()
}
