let label = if isReady { "ready" } else { "waiting" }

func grade(score: Int) -> String {
  return if score > 90 { "A" } else if score > 80 { "B" } else { "C" }
}
