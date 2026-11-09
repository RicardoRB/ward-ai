export function trust(req, $sce) {
  return $sce.trustAsHtml(req.query.html);
}
