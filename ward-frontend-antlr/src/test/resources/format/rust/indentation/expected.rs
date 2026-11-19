mod shapes {
    pub struct Point {
        pub x: i32,
        pub y: i32,
    }

    impl Point {
        pub fn new(x: i32, y: i32) -> Self {
            Point { x, y }
        }

        pub fn dist(&self) -> i32 {
            if self.x > 0 {
                self.x
            } else {
                -self.x
            }
        }
    }
}

fn call() {
    foo(
        first_argument_with_long_name_number_one,
        second_argument_with_long_name_number_two,
        third_argument_with_long_name_number_three,
    );
    let v = vec![
        first_element_with_long_name_number_one,
        second_element_with_long_name_number_two,
        third_element_with_long_name_number_three,
    ];
    run(|x| {
        let y = x + 1;
        y * 2
    });
}
