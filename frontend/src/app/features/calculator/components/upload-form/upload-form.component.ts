import {
  Component,
  input,
  output,
  signal,
  OnInit,
  inject,
  effect,
  DestroyRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AppInputComponent } from '../../../../shared/components/app-input/app-input.component';
import { AppDropzoneComponent } from '../../../../shared/components/app-dropzone/app-dropzone.component';
import { AppButtonComponent } from '../../../../shared/components/app-button/app-button.component';
import { AppCheckboxComponent } from '../../../../shared/components/app-checkbox/app-checkbox.component';
import { StlViewerComponent } from '../../../../shared/components/stl-viewer/stl-viewer.component';
import { PrintItemControlsComponent } from '../../../../shared/components/print-item-controls/print-item-controls.component';
import {
  QuoteRequest,
  QuoteRequestItem,
  QuoteEstimatorService,
  OptionsResponse,
  SimpleOption,
  MaterialOption,
  VariantOption,
} from '../../services/quote-estimator.service';
import { getColorHex } from '../../../../core/constants/colors.const';
import { LanguageService } from '../../../../core/services/language.service';
import {
  FormItem,
  ItemPrintSettingsUpdate,
  ItemSettingsDiffInfo,
  PrintSettingsSnapshot,
} from './upload-form.types';
import {
  easyModePresetForQuality,
  normalizeFileName,
  normalizeNumber,
  normalizeQualityValue,
  normalizeQuantity,
  normalizeText,
  sameItemSettings,
  toNozzleKey,
} from './upload-form-settings.util';

@Component({
  selector: 'app-upload-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TranslateModule,
    AppInputComponent,
    AppDropzoneComponent,
    AppButtonComponent,
    AppCheckboxComponent,
    StlViewerComponent,
    PrintItemControlsComponent,
  ],
  templateUrl: './upload-form.component.html',
  styleUrl: './upload-form.component.scss',
})
export class UploadFormComponent implements OnInit {
  mode = input<'easy' | 'advanced'>('easy');
  lockedSettings = input<boolean>(false);
  loading = input<boolean>(false);
  uploadProgress = input<number>(0);
  showSplitPrintingOption = input<boolean>(false);

  submitRequest = output<QuoteRequest>();
  itemQuantityChange = output<{
    index: number;
    fileName: string;
    quantity: number;
  }>();
  itemSettingsDiffChange = output<Record<string, ItemSettingsDiffInfo>>();
  printSettingsChange = output<{
    mode: 'easy' | 'advanced';
    material: string;
    quality: string;
    nozzleDiameter: number;
    layerHeight: number;
    infillDensity: number;
    infillPattern: string;
    supportEnabled: boolean;
  }>();

  private estimator = inject(QuoteEstimatorService);
  private fb = inject(FormBuilder);
  private translate = inject(TranslateService);
  private destroyRef = inject(DestroyRef);
  readonly languageService = inject(LanguageService);

  form: FormGroup;

  items = signal<FormItem[]>([]);
  selectedFile = signal<File | null>(null);
  sameSettingsForAll = signal(true);

  materials = signal<SimpleOption[]>([]);
  qualities = signal<SimpleOption[]>([]);
  nozzleDiameters = signal<SimpleOption[]>([]);
  infillPatterns = signal<SimpleOption[]>([]);
  layerHeights = signal<SimpleOption[]>([]);
  currentMaterialVariants = signal<VariantOption[]>([]);

  private fullMaterialOptions: MaterialOption[] = [];
  private optionsResponse: OptionsResponse | null = null;
  private usingFallbackOptions = false;
  private allNozzleDiameters: SimpleOption[] = [];
  private allLayerHeights: SimpleOption[] = [];
  private layerHeightsByNozzle: Record<string, SimpleOption[]> = {};
  private isPatchingSettings = false;
  private nextItemKey = 0;
  private readonly technicalMaterialMinLayerHeight = 0.12;
  private readonly technicalMaterialBlockedNozzles = [0.2, 0.8];

  acceptedFormats = '.stl,.3mf';
  private readonly allowedExtensions = ['stl', '3mf'] as const;

  constructor() {
    this.form = this.fb.group({
      itemsTouched: [false],
      syncAllItems: [true],
      material: ['', Validators.required],
      quality: ['standard', Validators.required],
      notes: [''],
      acceptSplitPrinting: [false],
      infillDensity: [15, [Validators.min(0), Validators.max(100)]],
      layerHeight: [0.2, [Validators.min(0.05), Validators.max(1.0)]],
      nozzleDiameter: [0.4, Validators.required],
      infillPattern: ['grid', Validators.required],
      supportEnabled: [true],
    });

    this.form.get('material')?.valueChanges.subscribe((value) => {
      const materialCode = String(value || '');
      this.updateVariants(materialCode);
      this.updatePrintOptionsForMaterial(materialCode);
    });

    this.form.get('quality')?.valueChanges.subscribe((quality) => {
      if (this.isPatchingSettings || this.mode() !== 'easy') {
        return;
      }
      this.applyEasyPresetFromQuality(String(quality || 'standard'));
    });

    this.form.get('nozzleDiameter')?.valueChanges.subscribe((nozzle) => {
      if (this.isPatchingSettings) {
        return;
      }
      this.updateLayerHeightOptionsForNozzle(nozzle, true);
    });

    this.form.valueChanges.subscribe(() => {
      if (this.isPatchingSettings) {
        return;
      }

      if (this.sameSettingsForAll()) {
        this.applyGlobalSettingsToAllItems();
      } else {
        this.syncSelectedItemSettingsFromForm();
      }

      this.emitPrintSettingsChange();
      this.emitItemSettingsDiffChange();
    });

    effect(() => {
      this.applySettingsLock(this.lockedSettings());
    });

    effect(() => {
      if (this.mode() !== 'easy' || this.sameSettingsForAll()) {
        return;
      }

      this.sameSettingsForAll.set(true);
      this.form.get('syncAllItems')?.setValue(true, { emitEvent: false });
      this.applyGlobalSettingsToAllItems();
      this.emitPrintSettingsChange();
      this.emitItemSettingsDiffChange();
    });

    effect(() => {
      if (this.mode() !== 'advanced') {
        return;
      }

      if (this.items().length > 0 || this.sameSettingsForAll()) {
        return;
      }

      this.sameSettingsForAll.set(true);
      this.form.get('syncAllItems')?.setValue(true, { emitEvent: false });
    });

    this.translate.onLangChange
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.refreshLocalizedOptions();
      });
  }

  ngOnInit() {
    this.estimator.getOptions().subscribe({
      next: (options: OptionsResponse) => {
        this.optionsResponse = options;
        this.usingFallbackOptions = false;
        this.refreshLocalizedOptions();
        this.setDefaults();
      },
      error: (err) => {
        console.error('Failed to load options', err);
        this.optionsResponse = null;
        this.usingFallbackOptions = true;
        this.refreshLocalizedOptions();
        this.allNozzleDiameters = [{ label: '0.4 mm', value: 0.4 }];
        this.nozzleDiameters.set(this.allNozzleDiameters);

        this.allLayerHeights = [{ label: '0.20 mm', value: 0.2 }];
        this.layerHeightsByNozzle = {
          [toNozzleKey(0.4)]: this.allLayerHeights,
        };

        this.setDefaults();
      },
    });
  }

  isStlFile(file: File | null): boolean {
    if (!file) return false;
    const name = file.name.toLowerCase();
    return name.endsWith('.stl');
  }

  isSupportedFile(file: File | null): boolean {
    if (!file) return false;

    const name = file.name.toLowerCase().trim();
    return this.allowedExtensions.some((ext) => name.endsWith(`.${ext}`));
  }

  canPreviewSelectedFile(): boolean {
    return this.isStlFile(this.getSelectedPreviewFile());
  }

  getSelectedPreviewFile(): File | null {
    const selected = this.selectedFile();
    if (!selected) return null;
    const item = this.items().find((i) => i.file === selected);
    return item ? item.previewFile || item.file : null;
  }

  getPreviewFilesByIndex(): Array<File | null> {
    return this.items().map((item) => item.previewFile ?? null);
  }

  getSelectedItemIndex(): number {
    const selected = this.selectedFile();
    if (!selected) return -1;
    return this.items().findIndex((item) => item.file === selected);
  }

  getSelectedItem(): FormItem | null {
    const index = this.getSelectedItemIndex();
    if (index < 0) return null;
    return this.items()[index] || null;
  }

  getVariantsForMaterial(
    materialCode: string | null | undefined,
  ): VariantOption[] {
    const normalized = String(materialCode || '')
      .trim()
      .toUpperCase();
    if (!normalized) return [];

    const found = this.fullMaterialOptions.find(
      (m) =>
        String(m.code || '')
          .trim()
          .toUpperCase() === normalized,
    );
    return found?.variants || [];
  }

  getLayerHeightOptionsForNozzle(nozzleRaw: unknown): SimpleOption[] {
    const key = toNozzleKey(nozzleRaw);
    const perNozzle = this.layerHeightsByNozzle[key];
    const available =
      perNozzle && perNozzle.length > 0
        ? perNozzle
        : this.allLayerHeights.length > 0
          ? this.allLayerHeights
          : [{ label: '0.20 mm', value: 0.2 }];

    if (!this.isTechnicalMaterial(this.form.get('material')?.value)) {
      return available;
    }

    return available.filter(
      (option) =>
        normalizeNumber(option.value, 0) >=
        this.technicalMaterialMinLayerHeight,
    );
  }

  onFilesDropped(newFiles: File[]) {
    const MAX_SIZE = 200 * 1024 * 1024;
    const validItems: FormItem[] = [];
    let hasInvalidType = false;
    let hasOversize = false;

    const defaults = this.getCurrentGlobalItemDefaults();

    for (const file of newFiles) {
      if (!this.isSupportedFile(file)) {
        hasInvalidType = true;
        continue;
      }

      if (file.size > MAX_SIZE) {
        hasOversize = true;
        continue;
      }

      const selection = this.getDefaultVariantSelection(defaults.material);
      validItems.push({
        clientKey: this.createItemClientKey(),
        file,
        previewFile: this.isStlFile(file) ? file : undefined,
        quantity: 1,
        material: defaults.material,
        quality: defaults.quality,
        color: selection.colorName,
        filamentVariantId: selection.filamentVariantId,
        supportEnabled: defaults.supportEnabled,
        infillDensity: defaults.infillDensity,
        infillPattern: defaults.infillPattern,
        layerHeight: defaults.layerHeight,
        nozzleDiameter: defaults.nozzleDiameter,
      });
    }

    if (hasInvalidType) {
      alert(this.translate.instant('CALC.ERR_INVALID_FILE_TYPE'));
    }

    if (hasOversize) {
      alert(this.translate.instant('CALC.ERR_FILE_TOO_LARGE'));
    }

    if (validItems.length === 0) {
      return;
    }

    this.items.update((current) => [...current, ...validItems]);
    this.form.get('itemsTouched')?.setValue(true);

    if (this.sameSettingsForAll()) {
      this.applyGlobalSettingsToAllItems();
    }

    this.selectFile(validItems[validItems.length - 1].file);
    this.emitItemSettingsDiffChange();
  }

  onAdditionalFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) {
      return;
    }

    this.onFilesDropped(Array.from(input.files));
    input.value = '';
  }

  updateItemQuantity(index: number, event: Event) {
    const input = event.target as HTMLInputElement;
    const parsed = parseInt(input.value, 10);
    const quantity = Number.isFinite(parsed) ? parsed : 1;

    this.updateItemQuantityValue(index, quantity);
  }

  updateItemQuantityValue(index: number, quantity: number) {
    const currentItem = this.items()[index];
    if (!currentItem) {
      return;
    }

    const normalizedQty = normalizeQuantity(quantity);
    this.updateItemQuantityByIndex(index, quantity);

    this.itemQuantityChange.emit({
      index,
      fileName: currentItem.file.name,
      quantity: normalizedQty,
    });
  }

  updateItemQuantityByIndex(index: number, quantity: number) {
    if (!Number.isInteger(index) || index < 0) return;
    const normalizedQty = normalizeQuantity(quantity);

    this.items.update((current) => {
      if (index >= current.length) return current;

      return current.map((item, idx) =>
        idx === index ? { ...item, quantity: normalizedQty } : item,
      );
    });
  }

  updateItemQuantityByName(fileName: string, quantity: number) {
    const targetName = normalizeFileName(fileName);
    const normalizedQty = normalizeQuantity(quantity);

    this.items.update((current) => {
      let matched = false;

      return current.map((item) => {
        if (!matched && normalizeFileName(item.file.name) === targetName) {
          matched = true;
          return { ...item, quantity: normalizedQty };
        }

        return item;
      });
    });
  }

  selectFile(file: File) {
    if (this.selectedFile() !== file) {
      this.selectedFile.set(file);
    }
    this.loadSelectedItemSettingsIntoForm();
  }

  getSelectedFileColor(): string {
    const selected = this.selectedFile();
    if (!selected) {
      return '#facf0a';
    }

    const item = this.items().find((i) => i.file === selected);
    if (!item) {
      return '#facf0a';
    }

    const variants = this.getVariantsForMaterial(item.material);
    if (variants.length > 0) {
      const byId =
        item.filamentVariantId != null
          ? variants.find((v) => v.id === item.filamentVariantId)
          : null;
      const byColor = variants.find((v) => v.colorName === item.color);
      const selectedVariant = byId || byColor;
      if (selectedVariant) {
        return selectedVariant.hexColor;
      }
    }

    return getColorHex(item.color);
  }

  updateItemColor(
    index: number,
    newSelection: string | { colorName: string; filamentVariantId?: number },
  ) {
    const colorName =
      typeof newSelection === 'string' ? newSelection : newSelection.colorName;
    const filamentVariantId =
      typeof newSelection === 'string'
        ? undefined
        : newSelection.filamentVariantId;

    this.items.update((current) => {
      if (index < 0 || index >= current.length) {
        return current;
      }

      return current.map((item, idx) =>
        idx === index
          ? {
              ...item,
              color: colorName,
              filamentVariantId,
            }
          : item,
      );
    });
  }

  removeItem(index: number) {
    let nextSelected: File | null = null;

    this.items.update((current) => {
      const updated = [...current];
      const removed = updated.splice(index, 1)[0];
      if (!removed) {
        return current;
      }

      if (this.selectedFile() === removed.file) {
        nextSelected =
          updated.length > 0 ? updated[Math.max(0, index - 1)].file : null;
      }

      return updated;
    });

    if (nextSelected) {
      this.selectFile(nextSelected);
    } else if (this.items().length === 0) {
      this.selectedFile.set(null);
    }

    this.emitItemSettingsDiffChange();
  }

  onSameSettingsToggle(enabled: boolean) {
    this.sameSettingsForAll.set(enabled);
    this.form.get('syncAllItems')?.setValue(enabled, { emitEvent: false });

    if (enabled) {
      this.applyGlobalSettingsToAllItems();
    } else {
      this.loadSelectedItemSettingsIntoForm();
    }

    this.emitPrintSettingsChange();
    this.emitItemSettingsDiffChange();
  }

  patchSettings(settings: any) {
    if (!settings) return;

    const patch: any = {};
    if (settings.materialCode) patch.material = settings.materialCode;
    if (settings.quality) {
      patch.quality = normalizeQualityValue(settings.quality);
    }

    const layer = Number(settings.layerHeightMm);
    if (Number.isFinite(layer)) {
      patch.layerHeight = layer;
      if (!patch.quality) {
        patch.quality =
          layer >= 0.24 ? 'draft' : layer <= 0.12 ? 'extra_fine' : 'standard';
      }
    }

    const nozzle = Number(settings.nozzleDiameterMm);
    if (Number.isFinite(nozzle)) patch.nozzleDiameter = nozzle;

    const infill = Number(settings.infillPercent);
    if (Number.isFinite(infill)) patch.infillDensity = infill;

    if (settings.infillPattern) patch.infillPattern = settings.infillPattern;
    if (settings.supportsEnabled !== undefined)
      patch.supportEnabled = Boolean(settings.supportsEnabled);
    if (settings.notes) patch.notes = settings.notes;

    this.isPatchingSettings = true;
    this.form.patchValue(patch, { emitEvent: false });
    this.isPatchingSettings = false;

    const materialCode = String(this.form.get('material')?.value || '');
    this.updateVariants(materialCode);
    this.updatePrintOptionsForMaterial(materialCode);

    if (this.sameSettingsForAll()) {
      this.applyGlobalSettingsToAllItems();
    } else {
      this.syncSelectedItemSettingsFromForm();
    }

    this.emitPrintSettingsChange();
    this.emitItemSettingsDiffChange();
  }

  setFiles(files: File[], options?: { autoSelect?: boolean }) {
    const defaults = this.getCurrentGlobalItemDefaults();
    const selection = this.getDefaultVariantSelection(defaults.material);
    const autoSelect = options?.autoSelect ?? true;

    const validItems: FormItem[] = files.map((file) => ({
      clientKey: this.createItemClientKey(),
      file,
      previewFile: this.isStlFile(file) ? file : undefined,
      quantity: 1,
      material: defaults.material,
      quality: defaults.quality,
      color: selection.colorName,
      filamentVariantId: selection.filamentVariantId,
      supportEnabled: defaults.supportEnabled,
      infillDensity: defaults.infillDensity,
      infillPattern: defaults.infillPattern,
      layerHeight: defaults.layerHeight,
      nozzleDiameter: defaults.nozzleDiameter,
    }));

    this.items.set(validItems);

    if (validItems.length > 0) {
      this.form.get('itemsTouched')?.setValue(true);
      if (autoSelect) {
        this.selectFile(validItems[validItems.length - 1].file);
      } else {
        this.selectedFile.set(null);
      }
    } else {
      this.selectedFile.set(null);
    }

    this.emitItemSettingsDiffChange();
  }

  setPreviewFileByIndex(index: number, previewFile: File) {
    if (!Number.isInteger(index) || index < 0) return;

    this.items.update((current) => {
      if (index >= current.length) return current;
      return current.map((item, idx) =>
        idx === index ? { ...item, previewFile } : item,
      );
    });
  }

  setItemReviewStateByIndex(
    index: number,
    level: 'warning' | 'error',
    message: string,
  ): void {
    if (!Number.isInteger(index) || index < 0) return;
    this.items.update((current) =>
      current.map((item, itemIndex) =>
        itemIndex === index
          ? { ...item, reviewLevel: level, reviewMessage: message }
          : item,
      ),
    );
  }

  setItemReviewStateByName(
    fileName: string,
    level: 'warning' | 'error',
    message: string,
  ): void {
    const normalizedName = normalizeFileName(fileName);
    this.items.update((current) =>
      current.map((item) =>
        normalizeFileName(item.file.name) === normalizedName
          ? { ...item, reviewLevel: level, reviewMessage: message }
          : item,
      ),
    );
  }

  setItemPrintSettingsByIndex(index: number, update: ItemPrintSettingsUpdate) {
    if (!Number.isInteger(index) || index < 0) return;

    this.items.update((current) => {
      if (index >= current.length) return current;

      return current.map((item, idx) => {
        if (idx !== index) {
          return item;
        }

        let next: FormItem = {
          ...item,
          ...update,
        };

        if (update.quality !== undefined) {
          next.quality = normalizeQualityValue(update.quality);
        }

        if (update.material !== undefined) {
          const variants = this.getVariantsForMaterial(update.material);
          const byId =
            next.filamentVariantId != null
              ? variants.find((v) => v.id === next.filamentVariantId)
              : null;
          const byColor = variants.find((v) => v.colorName === next.color);
          const fallback = variants.find((v) => !v.isOutOfStock) || variants[0];
          const variant = byId || byColor || fallback;
          if (variant) {
            next = {
              ...next,
              color: variant.colorName,
              filamentVariantId: variant.id,
            };
          }
        }

        return this.clampItemPrintSettings(next);
      });
    });

    this.refreshSameSettingsFlag();

    if (!this.sameSettingsForAll() && this.getSelectedItemIndex() === index) {
      this.loadSelectedItemSettingsIntoForm();
      this.emitPrintSettingsChange();
    }

    this.emitItemSettingsDiffChange();
  }

  restoreRequestDraft(
    request: QuoteRequest,
    options?: {
      sameSettingsForAll?: boolean;
      selectedFileName?: string | null;
      previewFiles?: Array<File | null>;
    },
  ) {
    if (!request?.items?.length) {
      return;
    }

    this.setFiles(
      request.items.map((item) => item.file),
      { autoSelect: false },
    );
    options?.previewFiles?.forEach((previewFile, index) => {
      if (previewFile) {
        this.setPreviewFileByIndex(index, previewFile);
      }
    });
    this.patchSettings({
      materialCode: request.material,
      quality: request.quality,
      layerHeightMm: request.layerHeight,
      nozzleDiameterMm: request.nozzleDiameter,
      infillPercent: request.infillDensity,
      infillPattern: request.infillPattern,
      supportsEnabled: request.supportEnabled,
      notes: request.notes,
    });
    this.form
      .get('acceptSplitPrinting')
      ?.setValue(Boolean(request.acceptSplitPrinting), { emitEvent: false });

    const sameSettingsForAll =
      this.mode() === 'advanced' ? (options?.sameSettingsForAll ?? true) : true;
    this.onSameSettingsToggle(sameSettingsForAll);

    request.items.forEach((item, index) => {
      this.updateItemQuantityByIndex(index, Number(item.quantity || 1));
      this.setItemPrintSettingsByIndex(index, {
        material: item.material ?? request.material,
        quality: item.quality ?? request.quality,
        nozzleDiameter: item.nozzleDiameter ?? request.nozzleDiameter,
        layerHeight: item.layerHeight ?? request.layerHeight,
        infillDensity: item.infillDensity ?? request.infillDensity,
        infillPattern: item.infillPattern ?? request.infillPattern,
        supportEnabled: item.supportEnabled ?? request.supportEnabled,
      });

      if (item.color) {
        this.updateItemColor(index, {
          colorName: item.color,
          filamentVariantId: item.filamentVariantId,
        });
      }
    });

    const selectedFileName = normalizeFileName(options?.selectedFileName ?? '');
    const target =
      this.items().find(
        (item) => normalizeFileName(item.file.name) === selectedFileName,
      ) ?? this.items()[this.items().length - 1];

    if (target) {
      this.selectFile(target.file);
    }

    this.emitPrintSettingsChange();
    this.emitItemSettingsDiffChange();
  }

  setAcceptSplitPrinting(accepted: boolean): void {
    this.form
      .get('acceptSplitPrinting')
      ?.setValue(accepted, { emitEvent: false });
  }

  getCurrentRequestDraft(): QuoteRequest {
    const defaults = this.getCurrentGlobalItemDefaults();

    const items: QuoteRequestItem[] = this.items().map((item) =>
      this.toRequestItem(item, defaults),
    );

    return {
      items,
      material: defaults.material,
      quality: defaults.quality,
      notes: this.form.get('notes')?.value || '',
      acceptSplitPrinting:
        this.showSplitPrintingOption() &&
        Boolean(this.form.get('acceptSplitPrinting')?.value),
      infillDensity: defaults.infillDensity,
      infillPattern: defaults.infillPattern,
      supportEnabled: defaults.supportEnabled,
      layerHeight: defaults.layerHeight,
      nozzleDiameter: defaults.nozzleDiameter,
      mode: this.mode(),
    };
  }

  onSubmit() {
    if (!this.form.valid || this.items().length === 0) {
      this.form.markAllAsTouched();
      this.form.get('itemsTouched')?.setValue(true);
      return;
    }

    this.submitRequest.emit(this.getCurrentRequestDraft());
  }

  private setDefaults() {
    if (this.materials().length > 0 && !this.form.get('material')?.value) {
      const exactPla = this.materials().find(
        (m) => typeof m.value === 'string' && m.value.toUpperCase() === 'PLA',
      );
      const fallback = exactPla || this.materials()[0];
      this.form.get('material')?.setValue(fallback.value, { emitEvent: false });
    }

    if (this.qualities().length > 0 && !this.form.get('quality')?.value) {
      const standard = this.qualities().find((q) => q.value === 'standard');
      this.form
        .get('quality')
        ?.setValue(standard ? standard.value : this.qualities()[0].value, {
          emitEvent: false,
        });
    }

    if (
      this.nozzleDiameters().length > 0 &&
      !this.form.get('nozzleDiameter')?.value
    ) {
      this.form.get('nozzleDiameter')?.setValue(0.4, { emitEvent: false });
    }

    if (
      this.infillPatterns().length > 0 &&
      !this.form.get('infillPattern')?.value
    ) {
      this.form
        .get('infillPattern')
        ?.setValue(this.infillPatterns()[0].value, { emitEvent: false });
    }

    const materialCode = String(this.form.get('material')?.value || '');
    this.updateVariants(materialCode);
    this.updatePrintOptionsForMaterial(materialCode);

    if (this.mode() === 'easy') {
      this.applyEasyPresetFromQuality(
        String(this.form.get('quality')?.value || 'standard'),
      );
    }

    this.emitPrintSettingsChange();
  }

  private applyEasyPresetFromQuality(qualityRaw: string) {
    const preset = easyModePresetForQuality(qualityRaw);

    this.isPatchingSettings = true;
    this.form.patchValue(
      {
        quality: preset.quality,
        nozzleDiameter: preset.nozzleDiameter,
        layerHeight: preset.layerHeight,
        infillDensity: preset.infillDensity,
        infillPattern: preset.infillPattern,
      },
      { emitEvent: false },
    );
    this.isPatchingSettings = false;

    this.updateLayerHeightOptionsForNozzle(preset.nozzleDiameter, true);
  }

  private getCurrentGlobalItemDefaults(): PrintSettingsSnapshot {
    const material = String(this.form.get('material')?.value || 'PLA');
    const quality = normalizeQualityValue(this.form.get('quality')?.value);

    if (this.mode() === 'easy') {
      const preset = easyModePresetForQuality(quality);
      return {
        material,
        quality: preset.quality,
        nozzleDiameter: preset.nozzleDiameter,
        layerHeight: preset.layerHeight,
        infillDensity: preset.infillDensity,
        infillPattern: preset.infillPattern,
        supportEnabled: Boolean(this.form.get('supportEnabled')?.value),
      };
    }

    return {
      material,
      quality,
      nozzleDiameter: normalizeNumber(
        this.form.get('nozzleDiameter')?.value,
        0.4,
      ),
      layerHeight: normalizeNumber(this.form.get('layerHeight')?.value, 0.2),
      infillDensity: normalizeNumber(this.form.get('infillDensity')?.value, 20),
      infillPattern: String(this.form.get('infillPattern')?.value || 'grid'),
      supportEnabled: Boolean(this.form.get('supportEnabled')?.value),
    };
  }

  private toRequestItem(
    item: FormItem,
    defaults: ReturnType<UploadFormComponent['getCurrentGlobalItemDefaults']>,
  ): QuoteRequestItem {
    const quality = normalizeQualityValue(item.quality || defaults.quality);

    if (this.mode() === 'easy') {
      const preset = easyModePresetForQuality(quality);
      return {
        file: item.file,
        quantity: normalizeQuantity(item.quantity),
        material: item.material || defaults.material,
        quality: preset.quality,
        color: item.color,
        filamentVariantId: item.filamentVariantId,
        supportEnabled: item.supportEnabled ?? defaults.supportEnabled,
        infillDensity: preset.infillDensity,
        infillPattern: preset.infillPattern,
        layerHeight: preset.layerHeight,
        nozzleDiameter: preset.nozzleDiameter,
      };
    }

    return {
      file: item.file,
      quantity: normalizeQuantity(item.quantity),
      material: item.material || defaults.material,
      quality,
      color: item.color,
      filamentVariantId: item.filamentVariantId,
      supportEnabled: item.supportEnabled,
      infillDensity: normalizeNumber(
        item.infillDensity,
        defaults.infillDensity,
      ),
      infillPattern: item.infillPattern || defaults.infillPattern,
      layerHeight: normalizeNumber(item.layerHeight, defaults.layerHeight),
      nozzleDiameter: normalizeNumber(
        item.nozzleDiameter,
        defaults.nozzleDiameter,
      ),
    };
  }

  private applyGlobalSettingsToAllItems() {
    const defaults = this.getCurrentGlobalItemDefaults();
    const variants = this.getVariantsForMaterial(defaults.material);
    const fallback = variants.find((v) => !v.isOutOfStock) || variants[0];

    this.items.update((current) =>
      current.map((item) => {
        const byId =
          item.filamentVariantId != null
            ? variants.find((v) => v.id === item.filamentVariantId)
            : null;
        const byColor = variants.find((v) => v.colorName === item.color);
        const selectedVariant = byId || byColor || fallback;

        return {
          ...item,
          material: defaults.material,
          quality: defaults.quality,
          nozzleDiameter: defaults.nozzleDiameter,
          layerHeight: defaults.layerHeight,
          infillDensity: defaults.infillDensity,
          infillPattern: defaults.infillPattern,
          supportEnabled: defaults.supportEnabled,
          color: selectedVariant ? selectedVariant.colorName : item.color,
          filamentVariantId: selectedVariant
            ? selectedVariant.id
            : item.filamentVariantId,
        };
      }),
    );
  }

  private syncSelectedItemSettingsFromForm() {
    if (this.sameSettingsForAll()) {
      return;
    }

    const index = this.getSelectedItemIndex();
    if (index < 0) {
      return;
    }

    const defaults = this.getCurrentGlobalItemDefaults();

    this.items.update((current) => {
      if (index >= current.length) return current;

      return current.map((item, idx) => {
        if (idx !== index) {
          return item;
        }

        const variants = this.getVariantsForMaterial(defaults.material);
        const byId =
          item.filamentVariantId != null
            ? variants.find((v) => v.id === item.filamentVariantId)
            : null;
        const byColor = variants.find((v) => v.colorName === item.color);
        const fallback = variants.find((v) => !v.isOutOfStock) || variants[0];
        const selectedVariant = byId || byColor || fallback;

        return {
          ...item,
          material: defaults.material,
          quality: defaults.quality,
          nozzleDiameter: defaults.nozzleDiameter,
          layerHeight: defaults.layerHeight,
          infillDensity: defaults.infillDensity,
          infillPattern: defaults.infillPattern,
          supportEnabled: defaults.supportEnabled,
          color: selectedVariant ? selectedVariant.colorName : item.color,
          filamentVariantId: selectedVariant
            ? selectedVariant.id
            : item.filamentVariantId,
        };
      });
    });
  }

  private loadSelectedItemSettingsIntoForm() {
    if (this.sameSettingsForAll()) {
      return;
    }

    const selected = this.getSelectedItem();
    if (!selected) {
      return;
    }

    this.isPatchingSettings = true;
    this.form.patchValue(
      {
        material: selected.material,
        quality: normalizeQualityValue(selected.quality),
        nozzleDiameter: selected.nozzleDiameter,
        layerHeight: selected.layerHeight,
        infillDensity: selected.infillDensity,
        infillPattern: selected.infillPattern,
        supportEnabled: selected.supportEnabled,
      },
      { emitEvent: false },
    );
    this.isPatchingSettings = false;

    this.updateVariants(selected.material);
    this.updatePrintOptionsForMaterial(selected.material);
  }

  private updateVariants(materialCode: string) {
    const variants = this.getVariantsForMaterial(materialCode);
    this.currentMaterialVariants.set(variants);

    if (this.sameSettingsForAll() || !this.selectedFile()) {
      return;
    }

    if (variants.length === 0) {
      return;
    }

    const selectedIndex = this.getSelectedItemIndex();
    if (selectedIndex < 0) {
      return;
    }

    this.items.update((current) => {
      if (selectedIndex >= current.length) {
        return current;
      }

      const selectedItem = current[selectedIndex];
      const byId =
        selectedItem.filamentVariantId != null
          ? variants.find((v) => v.id === selectedItem.filamentVariantId)
          : null;
      const byColor = variants.find((v) => v.colorName === selectedItem.color);
      const fallback = variants.find((v) => !v.isOutOfStock) || variants[0];
      const selectedVariant = byId || byColor || fallback;

      if (!selectedVariant) {
        return current;
      }

      return current.map((item, idx) =>
        idx === selectedIndex
          ? {
              ...item,
              color: selectedVariant.colorName,
              filamentVariantId: selectedVariant.id,
            }
          : item,
      );
    });
  }

  private updateLayerHeightOptionsForNozzle(
    nozzleRaw: unknown,
    clampCurrentLayer: boolean,
  ) {
    const options = this.getLayerHeightOptionsForNozzle(nozzleRaw);
    this.layerHeights.set(options);

    if (!clampCurrentLayer || options.length === 0) {
      return;
    }

    const currentLayer = normalizeNumber(
      this.form.get('layerHeight')?.value,
      options[0].value as number,
    );
    const allowed = options.some(
      (option) =>
        Math.abs(normalizeNumber(option.value, currentLayer) - currentLayer) <
        0.0001,
    );

    if (allowed) {
      return;
    }

    this.isPatchingSettings = true;
    this.form.patchValue(
      {
        layerHeight: Number(options[0].value),
      },
      { emitEvent: false },
    );
    this.isPatchingSettings = false;
  }

  private updatePrintOptionsForMaterial(materialCode: string) {
    const nozzles = this.isTechnicalMaterial(materialCode)
      ? this.allNozzleDiameters.filter(
          (option) =>
            !this.isTechnicalBlockedNozzle(
              normalizeNumber(option.value, Number.NaN),
            ),
        )
      : this.allNozzleDiameters;
    this.nozzleDiameters.set(nozzles);

    const currentNozzle = normalizeNumber(
      this.form.get('nozzleDiameter')?.value,
      0.4,
    );
    const nozzleAllowed = nozzles.some(
      (option) =>
        Math.abs(normalizeNumber(option.value, currentNozzle) - currentNozzle) <
        0.0001,
    );

    let effectiveNozzle = currentNozzle;
    if (!nozzleAllowed && nozzles.length > 0) {
      const standardNozzle = nozzles.find(
        (option) => Math.abs(normalizeNumber(option.value, 0) - 0.4) < 0.0001,
      );
      effectiveNozzle = Number((standardNozzle || nozzles[0]).value);
      this.isPatchingSettings = true;
      this.form
        .get('nozzleDiameter')
        ?.setValue(effectiveNozzle, { emitEvent: false });
      this.isPatchingSettings = false;
    }

    this.updateLayerHeightOptionsForNozzle(effectiveNozzle, true);
  }

  private isTechnicalMaterial(materialCode: unknown): boolean {
    const normalized = normalizeText(materialCode);
    return this.fullMaterialOptions.some(
      (material) =>
        normalizeText(material.code) === normalized && material.isTechnical,
    );
  }

  private isTechnicalBlockedNozzle(nozzle: number): boolean {
    return this.technicalMaterialBlockedNozzles.some(
      (blocked) => Math.abs(nozzle - blocked) < 0.0001,
    );
  }

  private clampItemPrintSettings(item: FormItem): FormItem {
    if (!this.isTechnicalMaterial(item.material)) {
      return item;
    }

    const allowedNozzles = this.allNozzleDiameters.filter(
      (option) =>
        !this.isTechnicalBlockedNozzle(
          normalizeNumber(option.value, Number.NaN),
        ),
    );
    const fallbackNozzle =
      allowedNozzles.find(
        (option) => Math.abs(normalizeNumber(option.value, 0) - 0.4) < 0.0001,
      ) || allowedNozzles[0];
    const nozzleDiameter = this.isTechnicalBlockedNozzle(item.nozzleDiameter)
      ? normalizeNumber(fallbackNozzle?.value, 0.4)
      : item.nozzleDiameter;
    const allowedLayers = this.getLayerHeightOptionsForItem(
      nozzleDiameter,
      item.material,
    );
    const layerAllowed = allowedLayers.some(
      (option) =>
        Math.abs(
          normalizeNumber(option.value, item.layerHeight) - item.layerHeight,
        ) < 0.0001,
    );

    return {
      ...item,
      nozzleDiameter,
      layerHeight: layerAllowed
        ? item.layerHeight
        : normalizeNumber(
            allowedLayers[0]?.value,
            this.technicalMaterialMinLayerHeight,
          ),
    };
  }

  private getLayerHeightOptionsForItem(
    nozzleRaw: unknown,
    materialCode: string,
  ): SimpleOption[] {
    const perNozzle = this.layerHeightsByNozzle[toNozzleKey(nozzleRaw)];
    const available =
      perNozzle && perNozzle.length > 0 ? perNozzle : this.allLayerHeights;
    if (!this.isTechnicalMaterial(materialCode)) {
      return available;
    }
    return available.filter(
      (option) =>
        normalizeNumber(option.value, 0) >=
        this.technicalMaterialMinLayerHeight,
    );
  }

  private emitPrintSettingsChange() {
    const defaults = this.getCurrentGlobalItemDefaults();
    this.printSettingsChange.emit({
      mode: this.mode(),
      material: defaults.material,
      quality: defaults.quality,
      nozzleDiameter: defaults.nozzleDiameter,
      layerHeight: defaults.layerHeight,
      infillDensity: defaults.infillDensity,
      infillPattern: defaults.infillPattern,
      supportEnabled: defaults.supportEnabled,
    });
  }

  private createItemClientKey(): string {
    this.nextItemKey += 1;
    return `upload-item-${this.nextItemKey}`;
  }

  private emitItemSettingsDiffChange() {
    if (this.sameSettingsForAll()) {
      this.itemSettingsDiffChange.emit({});
      return;
    }

    const baseline = this.getCurrentGlobalItemDefaults();
    const diffByFileName: Record<string, ItemSettingsDiffInfo> = {};

    this.items().forEach((item) => {
      const differences: string[] = [];

      if (normalizeText(item.material) !== normalizeText(baseline.material)) {
        differences.push(item.material.toUpperCase());
      }

      if (this.mode() === 'easy') {
        if (normalizeText(item.quality) !== normalizeText(baseline.quality)) {
          differences.push(`quality:${item.quality}`);
        }
      } else {
        if (
          Math.abs(
            normalizeNumber(item.nozzleDiameter, baseline.nozzleDiameter) -
              baseline.nozzleDiameter,
          ) > 0.0001
        ) {
          differences.push(`nozzle:${item.nozzleDiameter}`);
        }

        if (
          Math.abs(
            normalizeNumber(item.layerHeight, baseline.layerHeight) -
              baseline.layerHeight,
          ) > 0.0001
        ) {
          differences.push(`layer:${item.layerHeight}`);
        }

        if (
          Math.abs(
            normalizeNumber(item.infillDensity, baseline.infillDensity) -
              baseline.infillDensity,
          ) > 0.0001
        ) {
          differences.push(`infill:${item.infillDensity}%`);
        }

        if (
          normalizeText(item.infillPattern) !==
          normalizeText(baseline.infillPattern)
        ) {
          differences.push(`pattern:${item.infillPattern}`);
        }

        if (Boolean(item.supportEnabled) !== Boolean(baseline.supportEnabled)) {
          differences.push(
            `support:${Boolean(item.supportEnabled) ? 'on' : 'off'}`,
          );
        }
      }

      if (differences.length > 0) {
        diffByFileName[item.file.name] = { differences };
      }
    });

    this.itemSettingsDiffChange.emit(diffByFileName);
  }

  private getDefaultVariantSelection(materialCode: string): {
    colorName: string;
    filamentVariantId?: number;
  } {
    const variants = this.getVariantsForMaterial(materialCode);
    if (variants.length === 0) {
      return { colorName: 'Black' };
    }

    const preferred = variants.find((v) => !v.isOutOfStock) || variants[0];
    return {
      colorName: preferred.colorName,
      filamentVariantId: preferred.id,
    };
  }

  private localizedOptionLabel(key: string, fallback: string): string {
    const translated = this.translate.instant(key);
    return translated === key ? fallback : translated;
  }

  private refreshLocalizedOptions(): void {
    if (this.usingFallbackOptions) {
      this.materials.set([
        {
          label: this.translate.instant('CALC.FALLBACK_MATERIAL'),
          value: 'PLA',
        },
      ]);
      this.qualities.set([
        {
          label: this.translate.instant('CALC.FALLBACK_QUALITY_STANDARD'),
          value: 'standard',
        },
      ]);
      this.infillPatterns.set([
        {
          label: this.translate.instant('CALC.INFILL_PATTERNS.GRID'),
          value: 'grid',
        },
      ]);
      return;
    }

    const options = this.optionsResponse;
    if (!options) {
      return;
    }

    this.fullMaterialOptions = options.materials || [];
    this.materials.set(
      this.fullMaterialOptions.map((material) => ({
        label: this.localizeMaterialLabel(material),
        value: material.code,
      })),
    );
    this.qualities.set(
      (options.qualities || []).map((quality) => ({
        label: this.localizedOptionLabel(
          `CALC.QUALITY_OPTIONS.${quality.id.toUpperCase()}`,
          quality.label,
        ),
        value: quality.id,
      })),
    );
    this.infillPatterns.set(
      (options.infillPatterns || []).map((pattern) => ({
        label: this.localizedOptionLabel(
          `CALC.INFILL_PATTERNS.${pattern.id.toUpperCase()}`,
          pattern.label,
        ),
        value: pattern.id,
      })),
    );
    this.allNozzleDiameters = (options.nozzleDiameters || []).map((nozzle) => ({
      label: this.localizeBackendLabel(nozzle.label),
      value: nozzle.value,
    }));
    this.allLayerHeights = (options.layerHeights || []).map((layer) => ({
      label: layer.label,
      value: layer.value,
    }));
    this.layerHeightsByNozzle = {};
    (options.layerHeightsByNozzle || []).forEach((entry) => {
      this.layerHeightsByNozzle[toNozzleKey(entry.nozzleDiameter)] = (
        entry.layerHeights || []
      ).map((layer) => ({
        label: layer.label,
        value: layer.value,
      }));
    });

    this.updatePrintOptionsForMaterial(
      String(this.form.get('material')?.value || ''),
    );
  }

  private localizeBackendLabel(label: string): string {
    return String(label || '')
      .replace(
        /\(Standard\)$/,
        `(${this.translate.instant('CALC.OPTION_STANDARD')})`,
      )
      .replace(
        /\(Flexible\)$/,
        `(${this.translate.instant('CALC.OPTION_FLEXIBLE')})`,
      );
  }

  private localizeMaterialLabel(material: MaterialOption): string {
    const code = String(material.code || '').trim();
    const backendLabel = String(material.label || '');
    const typeKey = /\(Flexible\)$/i.test(backendLabel)
      ? 'CALC.OPTION_FLEXIBLE'
      : material.isTechnical
        ? 'CALC.OPTION_TECHNICAL'
        : 'CALC.OPTION_STANDARD';
    return `${code} (${this.translate.instant(typeKey)})`;
  }

  private refreshSameSettingsFlag() {
    const current = this.items();
    if (current.length <= 1) {
      return;
    }

    const first = current[0];
    const allEqual = current.every((item) => sameItemSettings(first, item));

    if (!allEqual) {
      this.sameSettingsForAll.set(false);
      this.form.get('syncAllItems')?.setValue(false, { emitEvent: false });
    }
  }

  private applySettingsLock(locked: boolean): void {
    const controlsToLock = [
      'syncAllItems',
      'material',
      'quality',
      'nozzleDiameter',
      'infillPattern',
      'layerHeight',
      'infillDensity',
      'supportEnabled',
    ];

    controlsToLock.forEach((name) => {
      const control = this.form.get(name);
      if (!control) return;

      if (locked) {
        control.disable({ emitEvent: false });
      } else {
        control.enable({ emitEvent: false });
      }
    });
  }
}
