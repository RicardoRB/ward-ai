class Helper {}
struct BarTests {}
class SharedBase: XCTestCase {}
final class LeafTests: SharedBase {}
final class Sealed: XCTestCase {}
open class Opened: Container.SharedBase {}
