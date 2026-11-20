pub fn first(values: &[u32]) -> u32 {
    read(values, 0)
}

fn read(values: &[u32], index: usize) -> u32 {
    unsafe { *values.get_unchecked(index) }
}

pub fn safe(values: &[u32]) -> u32 {
    values[0]
}
