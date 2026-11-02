import Foundation

public enum ForceTry {
    public static func bytes() -> Data {
        try! JSONEncoder().encode(1)
    }
}
