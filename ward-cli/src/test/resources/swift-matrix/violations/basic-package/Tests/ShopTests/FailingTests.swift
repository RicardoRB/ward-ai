import Testing
@testable import Shop

@Test func failsOnPurpose() {
    #expect(Cart().items == ["Book"])
}
