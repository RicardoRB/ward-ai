pub fn head(v: &[i32]) -> Option<i32> {
    let mut it = v.iter();
    it.next().copied()
}
