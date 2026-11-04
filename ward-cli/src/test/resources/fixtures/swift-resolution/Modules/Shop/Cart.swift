import Foundation
import struct Domain.Order

public struct Cart {
    public private(set) var orders: [Order] = []
    public struct Line {}
}
