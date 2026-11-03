import SwiftUI

struct Settings {
    @State private var isOn = false
    @Published var name: String = ""
    @Wrapper(limit: 10) var count: Int = 0
}

@propertyWrapper
struct Clamped {
    private var value: Int
    var wrappedValue: Int {
        get { value }
        set { value = min(max(newValue, 0), 100) }
    }
    init(wrappedValue: Int) {
        value = wrappedValue
    }
}
