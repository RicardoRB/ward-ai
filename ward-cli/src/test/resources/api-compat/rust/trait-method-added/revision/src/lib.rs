pub trait Draw {
    fn draw(&self);
    fn erase(&self);
    fn blink(&self) {}
}
