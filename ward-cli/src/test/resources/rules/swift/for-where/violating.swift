for user in users {
  if user.id == 1 { return true }
}
for subview in subviews {
  if !(subview is UIStackView) {
    subview.removeConstraints(subview.constraints)
  }
}
