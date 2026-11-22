//! Prices of order lines.

/// The price of `quantity` items at `unit` cents each, or `None` when it overflows.
pub fn line_total(unit: u32, quantity: u32) -> Option<u32> {
    unit.checked_mul(quantity)
}

/// The sum of the line totals, or `None` when it overflows.
pub fn order_total(lines: &[(u32, u32)]) -> Option<u32> {
    let mut total: u32 = 0;
    for (unit, quantity) in lines {
        total = total.checked_add(line_total(*unit, *quantity)?)?;
    }
    Some(total)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_line_costs_its_unit_price_times_the_quantity() {
        assert_eq!(line_total(250, 4), Some(1000));
    }

    #[test]
    fn a_line_that_overflows_has_no_total() {
        assert_eq!(line_total(u32::MAX, 2), None);
    }

    #[test]
    fn an_order_adds_its_lines() {
        assert_eq!(order_total(&[(100, 2), (50, 1)]), Some(250));
    }

    #[test]
    fn an_order_that_overflows_has_no_total() {
        assert_eq!(order_total(&[(u32::MAX, 1), (1, 1)]), None);
    }
}
