struct Row: View {
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    var order: Order

    var body: some View {
        HStack {
            let shape = RoundedRectangle(cornerRadius: 4, style: .continuous)
            StackView(items: order.items)
                .padding(2)
                #if os(iOS)
                .frame(width: 40, height: 40)
                #else
                .frame(width: 20, height: 20)
                #endif
                .background(in: shape)
                .overlay {
                    shape.strokeBorder(.quaternary, lineWidth: 0.5)
                }

            Text(order.id)
            TextField("Name", text: $text)
        }
    }
}
