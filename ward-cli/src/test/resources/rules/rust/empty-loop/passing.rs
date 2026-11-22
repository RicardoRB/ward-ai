pub fn hang() {
    loop {
        std::hint::spin_loop();
    }
}
