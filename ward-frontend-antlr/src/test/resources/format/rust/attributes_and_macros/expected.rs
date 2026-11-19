#![allow(dead_code)]

#[derive(Debug, Clone)]
struct A {
    x: i32,
}

#[cfg(test)]
mod tests {
    #[test]
    fn works() {
        println!("{} {}", 1, 2);
        assert_eq!(1 + 1, 2);
        let v = vec![1, 2, 3];
    }
}

macro_rules! twice {
    ($e:expr) => {
        $e + $e
    };
}
