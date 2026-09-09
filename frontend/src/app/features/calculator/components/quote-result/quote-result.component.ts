import {
  Component,
  input,
  output,
  signal,
  computed,
  effect,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AppCardComponent } from '../../../../shared/components/app-card/app-card.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';
import { SummaryCardComponent } from '../../../../shared/components/summary-card/summary-card.component';
import {
  PriceBreakdownComponent,
  PriceBreakdownRow,
} from '../../../../shared/components/price-breakdown/price-breakdown.component';
import { QuoteResult, QuoteItem } from '../../services/quote-estimator.service';

interface ItemSettingDetail {
  key: string;
  labelKey: string;
  value: string;
}

@Component({
  selector: 'app-quote-result',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    AppCardComponent,
    AppButtonComponent,
    SummaryCardComponent,
    PriceBreakdownComponent,
  ],
  templateUrl: './quote-result.component.html',
  styleUrl: './quote-result.component.scss',
})
export class QuoteResultComponent {
  readonly maxInputQuantity = 500;
  readonly directOrderLimit = 100;

  result = input.required<QuoteResult>();
  recalculationRequired = input<boolean>(false);
  itemSettingsDiffByFileName = input<Record<string, { differences: string[] }>>(
    {},
  );
  consult = output<void>();
  proceed = output<void>();
  itemChange = output<{
    id?: string;
    index: number;
    fileName: string;
    quantity: number;
  }>();
  itemQuantityPreviewChange = output<{
    id?: string;
    index: number;
    fileName: string;
    quantity: number;
  }>();

  // Local mutable state for items to handle quantity changes
  items = signal<QuoteItem[]>([]);
  private lastSentQuantities = new Map<string, number>();

  constructor() {
    effect(
      () => {
        // Initialize local items when result inputs change
        // We map to new objects to avoid mutating the input directly if it was a reference
        const nextItems = this.result().items.map((i) => ({ ...i }));
        this.items.set(nextItems);

        this.lastSentQuantities.clear();
        nextItems.forEach((item, index) => {
          const key = this.quantityKey(item, index);
          this.lastSentQuantities.set(key, item.quantity);
        });
      },
      { allowSignalWrites: true },
    );
  }

  updateQuantity(index: number, newQty: number | string) {
    const normalizedQty = this.normalizeQuantity(newQty);
    if (normalizedQty === null) return;

    const item = this.items()[index];
    if (!item) return;

    this.items.update((current) => {
      const updated = [...current];
      updated[index] = { ...updated[index], quantity: normalizedQty };
      return updated;
    });

    this.itemQuantityPreviewChange.emit({
      id: item.id,
      index,
      fileName: item.fileName,
      quantity: normalizedQty,
    });
  }

  flushQuantityUpdate(index: number): void {
    const item = this.items()[index];
    if (!item) return;

    const key = this.quantityKey(item, index);

    const normalizedQty = this.normalizeQuantity(item.quantity);
    if (normalizedQty === null) return;

    if (this.lastSentQuantities.get(key) === normalizedQty) {
      return;
    }

    this.itemChange.emit({
      id: item.id,
      index,
      fileName: item.fileName,
      quantity: normalizedQty,
    });
    this.lastSentQuantities.set(key, normalizedQty);
  }

  private quantityKey(item: QuoteItem, index: number): string {
    return item.id ? `id:${item.id}` : `index:${index}:${item.fileName}`;
  }

  hasQuantityOverLimit = computed(() =>
    this.items().some((item) => item.quantity > this.directOrderLimit),
  );

  hasSplitPrintingItems = computed(() =>
    this.items().some((item) => item.requiresSplitPrinting === true),
  );

  costBreakdown = computed(() => {
    const currentItems = this.items();
    const cad = this.result().cadTotal || 0;

    let subtotal = cad;
    currentItems.forEach((item) => {
      subtotal += item.unitPrice * item.quantity;
    });

    const nozzleChange = Math.max(0, this.result().nozzleChangeCost || 0);
    const baseSetupRaw =
      this.result().baseSetupCost != null
        ? this.result().baseSetupCost
        : this.result().setupCost - nozzleChange;
    const baseSetup = Math.max(0, baseSetupRaw || 0);
    const total = subtotal + baseSetup + nozzleChange;

    return {
      subtotal: Math.round(subtotal * 100) / 100,
      baseSetup: Math.round(baseSetup * 100) / 100,
      nozzleChange: Math.round(nozzleChange * 100) / 100,
      total: Math.round(total * 100) / 100,
    };
  });

  priceBreakdownRows = computed<PriceBreakdownRow[]>(() => {
    const breakdown = this.costBreakdown();

    return [
      {
        labelKey: 'CHECKOUT.SUBTOTAL',
        amount: breakdown.subtotal,
      },
      {
        labelKey: 'CHECKOUT.SETUP_FEE',
        amount: breakdown.baseSetup,
      },
      {
        labelKey: 'CHECKOUT.NOZZLE_CHANGE',
        amount: breakdown.nozzleChange,
        visible: breakdown.nozzleChange > 0,
      },
    ];
  });

  totals = computed(() => {
    const currentItems = this.items();
    let time = 0;
    let weight = 0;

    currentItems.forEach((i) => {
      time += i.unitTime * i.quantity;
      weight += i.unitWeight * i.quantity;
    });

    const hours = Math.floor(time / 3600);
    const minutes = Math.ceil((time % 3600) / 60);

    return {
      price: this.costBreakdown().total,
      hours,
      minutes,
      weight: Math.ceil(weight),
    };
  });

  private normalizeQuantity(newQty: number | string): number | null {
    const qty = typeof newQty === 'string' ? parseInt(newQty, 10) : newQty;
    if (!Number.isFinite(qty) || qty < 1) {
      return null;
    }
    return Math.min(qty, this.maxInputQuantity);
  }

  getItemSettingDetails(item: QuoteItem): ItemSettingDetail[] {
    const emittedDifferences =
      this.itemSettingsDiffByFileName()[item.fileName]?.differences || [];
    const hasCalculatedSettings =
      item.material != null ||
      item.quality != null ||
      item.nozzleDiameter != null ||
      item.layerHeight != null ||
      item.infillDensity != null ||
      item.infillPattern != null ||
      item.supportEnabled != null;
    const differences = hasCalculatedSettings
      ? this.getDifferencesFromStandard(item)
      : emittedDifferences;

    return differences
      .map((difference): ItemSettingDetail | null => {
        const separatorIndex = difference.indexOf(':');

        if (separatorIndex < 0) {
          return {
            key: 'material',
            labelKey: 'CALC.MATERIAL',
            value: String(item.material || difference).toUpperCase(),
          };
        }

        const key = difference.slice(0, separatorIndex);
        const rawValue = difference.slice(separatorIndex + 1);

        switch (key) {
          case 'quality':
            return {
              key,
              labelKey: 'CALC.QUALITY',
              value: this.formatOptionValue(item.quality || rawValue),
            };
          case 'nozzle':
            return {
              key,
              labelKey: 'CALC.NOZZLE',
              value: `${item.nozzleDiameter ?? rawValue} mm`,
            };
          case 'layer':
            return {
              key,
              labelKey: 'CALC.LAYER_HEIGHT',
              value: `${item.layerHeight ?? rawValue} mm`,
            };
          case 'infill':
            return {
              key,
              labelKey: 'CALC.INFILL',
              value: `${item.infillDensity ?? rawValue.replace('%', '')}%`,
            };
          case 'pattern':
            return {
              key,
              labelKey: 'CALC.PATTERN',
              value: this.formatOptionValue(item.infillPattern || rawValue),
            };
          case 'support':
            return {
              key,
              labelKey: 'CALC.SUPPORT',
              value:
                typeof item.supportEnabled === 'boolean'
                  ? item.supportEnabled
                    ? 'ON'
                    : 'OFF'
                  : rawValue.toUpperCase(),
            };
          default:
            return null;
        }
      })
      .filter((detail): detail is ItemSettingDetail => detail !== null);
  }

  private getDifferencesFromStandard(item: QuoteItem): string[] {
    const differences: string[] = [];

    if (item.material && item.material.trim().toUpperCase() !== 'PLA') {
      differences.push(item.material);
    }
    if (item.quality && item.quality.trim().toLowerCase() !== 'standard') {
      differences.push(`quality:${item.quality}`);
    }
    if (
      item.nozzleDiameter != null &&
      Math.abs(item.nozzleDiameter - 0.4) > 0.0001
    ) {
      differences.push(`nozzle:${item.nozzleDiameter}`);
    }
    if (item.layerHeight != null && Math.abs(item.layerHeight - 0.2) > 0.0001) {
      differences.push(`layer:${item.layerHeight}`);
    }
    if (
      item.infillDensity != null &&
      Math.abs(item.infillDensity - 15) > 0.0001
    ) {
      differences.push(`infill:${item.infillDensity}%`);
    }
    if (
      item.infillPattern &&
      item.infillPattern.trim().toLowerCase() !== 'grid'
    ) {
      differences.push(`pattern:${item.infillPattern}`);
    }
    if (item.supportEnabled === false) {
      differences.push('support:off');
    }

    return differences;
  }

  private formatOptionValue(value: string): string {
    return String(value || '')
      .replace(/[_-]+/g, ' ')
      .replace(/\b\w/g, (character) => character.toUpperCase());
  }
}
