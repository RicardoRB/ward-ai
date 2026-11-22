pub fn open() {}
pub fn close() {}
fn helper() {}
pub struct Door { pub width: u32, depth: u32 }
impl Door {
    pub fn lock(&self) {}
    pub fn unlock(&self) {}
    fn secret(&self) {}
}
pub enum Mode { Fast, Slow }
pub trait Draw { fn draw(&self) {} }
