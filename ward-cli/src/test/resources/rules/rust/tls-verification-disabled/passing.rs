fn client(strict: bool) {
    let client = reqwest::Client::builder()
        .danger_accept_invalid_certs(false)
        .build()
        .unwrap();
    let other = reqwest::Client::builder().danger_accept_invalid_certs(strict).build();
}
