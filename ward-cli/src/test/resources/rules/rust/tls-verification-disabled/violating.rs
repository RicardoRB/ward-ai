fn client() {
    let client = reqwest::Client::builder()
        .danger_accept_invalid_certs(true)
        .build()
        .unwrap();
    let other = reqwest::blocking::Client::builder().danger_accept_invalid_hostnames( true ).build();
}

fn config() {
    let config = rustls::ClientConfig::builder()
        .dangerous()
        .with_custom_certificate_verifier(verifier)
        .with_no_client_auth();
}
