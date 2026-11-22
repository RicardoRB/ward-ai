//! A Ward rule finding of error severity: a statement that is a bare name.

/// Returns the value after a statement that does nothing.
pub fn show(value: u32) -> u32 {
    value;
    value
}
