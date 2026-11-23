use std::collections::HashMap;

pub fn make() -> HashMap<i32, i32> {
    HashMap::new()
}

mod aliased {
    use std::{self as core_std};

    pub fn limit() -> u32 {
        core_std::u32::MAX
    }
}
