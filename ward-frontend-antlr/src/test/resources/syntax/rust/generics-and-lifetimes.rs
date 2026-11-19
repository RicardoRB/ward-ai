use std::collections::HashMap;
use std::fmt::{self, Debug, Display};

pub struct Cache<'a, K: Eq + std::hash::Hash, V = String> where V: Clone {
    items: HashMap<K, V>,
    label: &'a str,
}

impl<'a, K, V> Cache<'a, K, V>
where
    K: Eq + std::hash::Hash + Clone,
    V: Clone + Default,
{
    pub const CAPACITY: usize = 16;

    pub fn new(label: &'a str) -> Self {
        Self { items: HashMap::new(), label }
    }

    pub fn get_or_default<'b>(&'b mut self, key: &K) -> &'b V where 'a: 'b {
        self.items.entry(key.clone()).or_default()
    }
}

fn longest<'x>(left: &'x str, right: &'x str) -> &'x str {
    if left.len() >= right.len() { left } else { right }
}

fn apply<F, const N: usize>(values: [i32; N], f: F) -> [i32; N] where F: Fn(i32) -> i32 {
    let mut out = [0; N];
    for (slot, value) in out.iter_mut().zip(values) {
        *slot = f(value);
    }
    out
}

fn higher_ranked<F>(f: F) where for<'r> F: Fn(&'r str) -> &'r str {
    let _ = f("x");
}

impl<K: Debug, V: Display> fmt::Display for Cache<'_, K, V> {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}", self.label)
    }
}

type Pair<T> = (T, T);
type Callback = Box<dyn Fn(u8) -> u8 + Send + 'static>;
