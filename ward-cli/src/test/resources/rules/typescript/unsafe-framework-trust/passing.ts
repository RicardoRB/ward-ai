export class View {
  html() {
    return this.sanitizer.bypassSecurityTrustHtml('<b>fixed</b>');
  }

  clean(html) {
    return this.sanitizer.sanitize(1, html);
  }
}

export class Widths {
  style(percent: number) {
    return this.sanitizer.bypassSecurityTrustStyle('width: ' + percent * 2 + '%');
  }
}

const SNIPPETS = ['<b>a</b>', '<i>b</i>'];

export class Snippets {
  first() {
    for (const snippet of SNIPPETS) {
      return this.sanitizer.bypassSecurityTrustHtml(snippet);
    }
  }
}
