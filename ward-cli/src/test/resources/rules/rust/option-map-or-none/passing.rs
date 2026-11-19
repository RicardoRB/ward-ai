pub fn bump(o: Option<i32>) -> Option<i32> {
    o.and_then(|x| Some(x + 1))
}
