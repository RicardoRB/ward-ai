fn show<T>(value: T) -> String
where
    T: Display,
    T: Clone,
{
    value.to_string()
}

impl<T> Holder<T>
where
    T: Copy,
{
    fn get(&self) -> T {
        self.0
    }
}

trait Named
where
    Self: Sized,
{
}
