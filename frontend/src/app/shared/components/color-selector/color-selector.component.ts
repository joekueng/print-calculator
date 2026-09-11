import {
  Component,
  input,
  output,
  signal,
  computed,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import {
  PRODUCT_COLORS,
  getColorHex,
  ColorCategory,
  ColorOption,
  resolveLocalizedColorLabel,
} from '../../../core/constants/colors.const';
import { VariantOption } from '../../../features/calculator/services/quote-estimator.service';
import { LanguageService } from '../../../core/services/language.service';

@Component({
  selector: 'app-color-selector',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './color-selector.component.html',
  styleUrl: './color-selector.component.scss',
})
export class ColorSelectorComponent {
  private readonly languageService = inject(LanguageService);
  disabled = input(false);
  selectedColor = input<string>('Black');
  selectedVariantId = input<number | null>(null);
  variants = input<VariantOption[]>([]);
  colorSelected = output<{ colorName: string; filamentVariantId?: number }>();

  isOpen = signal(false);

  categories = computed(() => {
    const vars = this.variants();
    if (vars && vars.length > 0) {
      const byFinish = new Map<string, ColorOption[]>();
      vars.forEach((v) => {
        const finish = this.finishCategoryLabel(v.finishType);
        const bucket = byFinish.get(finish) || [];
        bucket.push({
          label:
            resolveLocalizedColorLabel(this.languageService.selectedLang(), {
              fallback: v.colorName,
              it: v.colorLabelIt,
              en: v.colorLabelEn,
              de: v.colorLabelDe,
              fr: v.colorLabelFr,
            }) ?? v.colorName,
          value: v.colorName,
          hex: v.hexColor,
          variantId: v.id,
          outOfStock: v.isOutOfStock,
        });
        byFinish.set(finish, bucket);
      });

      return Array.from(byFinish.entries()).map(([finish, colors]) => ({
        name: finish,
        colors,
      })) as ColorCategory[];
    }
    return PRODUCT_COLORS;
  });

  toggleOpen() {
    if (this.disabled()) return;
    this.isOpen.update((v) => !v);
  }

  selectColor(color: ColorOption) {
    if (this.disabled() || color.outOfStock) return;

    this.colorSelected.emit({
      colorName: color.value,
      filamentVariantId: color.variantId,
    });
    this.isOpen.set(false);
  }

  // Helper to find hex for the current selected value
  getCurrentHex(): string {
    // Check in dynamic variants first
    const vars = this.variants();
    if (vars && vars.length > 0) {
      const found = vars.find((v) => v.colorName === this.selectedColor());
      if (found) return found.hexColor;
    }

    return getColorHex(this.selectedColor());
  }

  getCurrentLabel(): string {
    for (const category of this.categories()) {
      const color = category.colors.find(
        (entry) => entry.value === this.selectedColor(),
      );
      if (color) return color.label;
    }
    return this.selectedColor();
  }

  private finishCategoryLabel(finishType: string): string {
    const normalized = String(finishType || '')
      .trim()
      .toLowerCase();
    if (normalized === 'glossy') return 'COLOR.CATEGORY_GLOSSY';
    if (normalized === 'matte') return 'COLOR.CATEGORY_MATTE';
    return 'COLOR.AVAILABLE_COLORS';
  }

  close() {
    this.isOpen.set(false);
  }
}
