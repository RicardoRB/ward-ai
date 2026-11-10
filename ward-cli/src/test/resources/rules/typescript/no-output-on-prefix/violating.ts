import { Directive, EventEmitter, Output } from '@angular/core';

@Directive({ selector: '[appH]' })
export class HDirective {
  @Output() onSave = new EventEmitter<void>();
  @Output('on') done = new EventEmitter<void>();
  @Output() saved = new EventEmitter<void>();
}
