#if !canImport(FoundationNetworking)
import Network
#endif

func sample(_ value: Any, items: [Int], flag: Bool, name: String?) throws {
    let number = value as? Int
    let forced = value as! String
    let data = try? read()
    let other = try! read()
    let quality = 1.0 - (Double(items.count) * 0.1)
    let scaled = timing * (3.0 - 2.0 * timing)
    let status = name.map(Status.init) ?? .unknown
    let list = (value as? [Int]) ?? []
    let pair = items.first ?? (0)
    let direction = flag ? -1.0 : 1.0
    let total = items.reduce(0) { $0 + $1 }
    let binding = $text.wrappedValue
    let range = 80.0...120.0
    let head = items[..<(items.count - 1)]
    for offset in 0..<5 where offset != 2 {
        print(offset, in: range, for: number)
    }
    print(forced, data, other, quality, scaled, status, list, pair, direction, total, binding, head)
}

#if compiler(>=6)
func check<T: Decodable>(of type: T.Type = T.self) -> Box<T> {
    guard #available(macOS 13, iOS 16, *) else { return Box<T>(nil) }
    let label = "🍩 ready"
    let first = items.first { $0.isReady }?.name
    return Box<T>(label, first)
}

func after() {}
#endif
