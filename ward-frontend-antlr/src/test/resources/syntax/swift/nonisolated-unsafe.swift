nonisolated(unsafe) var counter = 0

actor Worker {
  nonisolated(unsafe) var cache: [String] = []
}
