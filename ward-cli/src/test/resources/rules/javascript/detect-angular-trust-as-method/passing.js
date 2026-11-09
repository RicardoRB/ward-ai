export function trust(req, $sce) {
  return $sce.trustAsHtml('<b>ok</b>');
}
