pub struct MyStruct;

pub enum Mode {
    Fast,
    Slow,
}

#[derive(Default)]
pub enum Choice {
    #[default]
    Auto,
    #[cfg(unix)]
    Native,
}

#[allow(non_camel_case_types)]
pub type uid_t = u32;
