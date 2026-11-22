pub fn grab(o: &mut Option<i32>) -> Option<i32> {
    std::mem::replace(o, None)
}
