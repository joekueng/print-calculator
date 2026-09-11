import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ColorSelectorComponent } from '../color-selector/color-selector.component';
import { VariantOption } from '../../../features/calculator/services/quote-estimator.service';

@Component({
  selector: 'app-print-item-controls',
  standalone: true,
  imports: [FormsModule, TranslateModule, ColorSelectorComponent],
  templateUrl: './print-item-controls.component.html',
  styleUrl: './print-item-controls.component.scss',
})
export class PrintItemControlsComponent {
  quantity = input(1);
  color = input('');
  variantId = input<number | null>(null);
  variants = input<VariantOption[]>([]);
  disabled = input(false);
  commitOnBlur = input(false);
  quantityChange = output<number>();
  colorChange = output<{ colorName: string; filamentVariantId?: number }>();
}
