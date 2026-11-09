export function trust(req, sanitizer) {
  return sanitizer.bypassSecurityTrustHtml('<b>ok</b>');
}
