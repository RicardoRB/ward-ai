//! A module no test calls.

/// Picks the larger of two numbers.
pub fn larger(a: u32, b: u32) -> u32 {
    if a > b {
        a
    } else {
        b
    }
}

/// Picks the smaller of two numbers.
pub fn smaller(a: u32, b: u32) -> u32 {
    if a < b {
        a
    } else {
        b
    }
}
