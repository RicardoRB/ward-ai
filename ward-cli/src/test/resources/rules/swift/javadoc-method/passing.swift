/**
 * Adds two numbers.
 * - Parameter a: first number.
 * - Parameter b: second number.
 * - Returns: the sum.
 */
func add(a: Int, b: Int) -> Int { a + b }

/// Converts the value.
///
/// - Parameters:
///   - value: The value to convert
///   - clamped: Whether the result stays in the range
/// - Returns: The converted value.
/// - Throws: An error for a value that is not finite.
func convert(value: Double, clamped: Bool) throws -> Double { value }

// MARK: Not documented

func undocumented(count: Int) throws -> Int { count }
