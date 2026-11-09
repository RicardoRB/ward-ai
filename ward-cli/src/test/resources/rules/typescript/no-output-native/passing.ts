import { Directive, EventEmitter, Output } from '@angular/core';

@Directive({ selector: '[appG]' })
export class GDirective {
  @Output() selectedItem = new EventEmitter<void>();
  @Output('itemSelected') done = new EventEmitter<void>();
}
