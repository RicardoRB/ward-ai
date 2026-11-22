//! A clippy warning: the return is the last statement.

/// Returns the length.
pub fn length(items: &[u8]) -> usize {
    return items.len();
}
