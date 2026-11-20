pub fn first(values: &[u32]) -> u32 {
    unsafe { *values.get_unchecked(0) }
}

pub(crate) fn last(values: &[u32]) -> u32 {
    let n = values.len() - 1;
    unsafe { *values.get_unchecked(n) }
}
