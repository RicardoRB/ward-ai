export function render(req, ref) {
  ref.current.innerHTML = req.query.html;
}
