fun build() {
    val result = assembleReport(
            customerName,
            invoiceNumber,
            shippingAddress,
            billingAddress,
            totalAmount
    )
    val short = call(a, b)
}
