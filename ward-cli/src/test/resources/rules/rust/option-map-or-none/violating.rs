pub fn bump(o: Option<i32>) -> Option<i32> {
    o.map_or(None, |x| Some(x + 1))
}
