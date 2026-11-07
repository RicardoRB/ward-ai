func load() async -> Int {
  async let first = fetchFirst()
  async let second = fetchSecond()
  return await first + second
}
