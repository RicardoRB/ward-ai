export class View {
  html(html) {
    return this.sanitizer.bypassSecurityTrustHtml(html);
  }

  url(address) {
    return this.sanitizer.bypassSecurityTrustResourceUrl(address);
  }
}

export class Styles {
  style(color: string) {
    return this.sanitizer.bypassSecurityTrustStyle('color: ' + color);
  }
}

const FILLED = ['<b>a</b>'];

export class Filled {
  fill(markup: string) {
    FILLED.push(markup);
  }

  first() {
    for (const snippet of FILLED) {
      return this.sanitizer.bypassSecurityTrustHtml(snippet);
    }
  }
}
