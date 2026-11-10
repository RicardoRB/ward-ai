export function trust(req, sanitizer) {
  return sanitizer.bypassSecurityTrustHtml(req.query.html);
}
