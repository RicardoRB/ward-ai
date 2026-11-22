use shop::pricing::order_total;

#[test]
fn an_empty_order_costs_nothing() {
    assert_eq!(order_total(&[]), Some(0));
}
