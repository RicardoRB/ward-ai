func fetchUser(id: Int) async throws -> String {
    let data = try await load(id)
    return data
}

func run() async {
    async let first = fetchUser(id: 1)
    async let second = fetchUser(id: 2)
    let names = await [first, second]
    print(names)
}
