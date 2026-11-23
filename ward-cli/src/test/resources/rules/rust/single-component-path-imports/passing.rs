pub fn limit() -> u32 {
    std::u32::MAX
}

mod aliased {
    use std as core_std;
    use serde_json as json;

    pub fn limit() -> u32 {
        core_std::u32::MAX
    }
}

pub use bytes;
