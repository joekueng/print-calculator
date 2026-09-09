import {
  AfterViewInit,
  Component,
  computed,
  signal,
  ViewChild,
  ElementRef,
  Inject,
  OnInit,
  Optional,
  PLATFORM_ID,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { AppAlertComponent } from '../../shared/components/app-alert/app-alert.component';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { BrandAnimationLogoComponent } from '../../shared/components/brand-animation-logo/brand-animation-logo.component';
import { UploadFormComponent } from './components/upload-form/upload-form.component';
import { QuoteResultComponent } from './components/quote-result/quote-result.component';
import {
  PendingCalculatorDraft,
  QuoteCalculationFailure,
  QuoteRequest,
  QuoteResult,
  QuoteEstimatorService,
} from './services/quote-estimator.service';
import { SuccessStateComponent } from '../../shared/components/success-state/success-state.component';
import { Router, ActivatedRoute } from '@angular/router';
import { LanguageService } from '../../core/services/language.service';

type TrackedPrintSettings = {
  mode: 'easy' | 'advanced';
  material: string;
  quality: string;
  nozzleDiameter: number;
  layerHeight: number;
  infillDensity: number;
  infillPattern: string;
  supportEnabled: boolean;
};

type TrackedPrintItemState = {
  fileName: string;
  settings: TrackedPrintSettings;
};

type PendingSessionRestore = {
  session: any;
  items: any[];
  files: File[];
  preserveError: boolean;
  previewFiles: Array<{
    index: number;
    file: File;
  }>;
};

@Component({
  selector: 'app-calculator-page',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    AppCardComponent,
    AppAlertComponent,
    AppButtonComponent,
    BrandAnimationLogoComponent,
    UploadFormComponent,
    QuoteResultComponent,
    SuccessStateComponent,
  ],
  templateUrl: './calculator-page.component.html',
  styleUrl: './calculator-page.component.scss',
})
export class CalculatorPageComponent implements OnInit, AfterViewInit {
  private readonly isBrowser: boolean;
  mode = signal<'easy' | 'advanced'>('easy');
  step = signal<'upload' | 'quote' | 'details' | 'success'>('upload');

  loading = signal(false);
  uploadProgress = signal(0);
  result = signal<QuoteResult | null>(null);
  cadSessionLocked = signal(false);
  error = signal<boolean>(false);
  errorKey = signal<string>('CALC.ERROR_GENERIC');
  errorMessage = signal<string | null>(null);
  errorCode = signal<string | null>(null);
  warningMessage = signal<string | null>(null);
  showErrorAlert = computed(() => this.error() && !this.result());
  isZeroQuoteError = computed(
    () => this.error() && this.errorKey() === 'CALC.ERROR_ZERO_PRICE',
  );
  isCustomQuoteError = computed(
    () =>
      this.error() &&
      (this.errorCode() === 'MODEL_REQUIRES_CUSTOM_QUOTE' ||
        this.errorCode() === 'MODEL_OUT_OF_PRINT_VOLUME' ||
        this.errorCode() === 'MODEL_PROCESSING_FAILED'),
  );
  isOutOfVolumeError = computed(
    () => this.errorCode() === 'MODEL_OUT_OF_PRINT_VOLUME',
  );
  showSplitPrintingOption = computed(() => {
    const result = this.result();

    return (
      this.errorCode() === 'MODEL_OUT_OF_PRINT_VOLUME' ||
      (result?.failedItems || []).some(
        (failure) => failure.code === 'MODEL_OUT_OF_PRINT_VOLUME',
      ) ||
      (result?.items || []).some((item) => item.requiresSplitPrinting === true)
    );
  });
  readonly faqIds = [
    'FILES',
    'MODE',
    'NO_MODEL',
    'PRICE',
    'BEFORE_UPLOAD',
  ] as const;
  readonly modelSources = [
    {
      id: 'PRINTABLES',
      label: 'Printables',
      url: 'https://www.printables.com',
    },
    {
      id: 'MAKERWORLD',
      label: 'MakerWorld',
      url: 'https://makerworld.com',
    },
    {
      id: 'THINGIVERSE',
      label: 'Thingiverse',
      url: 'https://www.thingiverse.com',
    },
    {
      id: 'THANGS',
      label: 'Thangs',
      url: 'https://thangs.com',
    },
    {
      id: 'CULTS3D',
      label: 'Cults3D',
      url: 'https://cults3d.com',
    },
    {
      id: 'YEGGI',
      label: 'Yeggi',
      url: 'https://www.yeggi.com',
    },
  ] as const;
  readonly favoriteModelSourceIds = ['PRINTABLES', 'MAKERWORLD'] as const;
  readonly favoriteModelSources = this.modelSources.filter((source) =>
    this.favoriteModelSourceIds.includes(
      source.id as (typeof this.favoriteModelSourceIds)[number],
    ),
  );
  readonly otherModelSources = this.modelSources.filter(
    (source) =>
      !this.favoriteModelSourceIds.includes(
        source.id as (typeof this.favoriteModelSourceIds)[number],
      ),
  );

  orderSuccess = signal(false);
  requiresRecalculation = signal(false);
  itemSettingsDiffByFileName = signal<
    Record<string, { differences: string[] }>
  >({});
  private baselinePrintSettings: TrackedPrintSettings | null = null;
  private baselineItemStates: TrackedPrintItemState[] = [];
  private pendingSessionRestore: PendingSessionRestore | null = null;
  private isRestoringQuoteState = false;
  private restoreDraftWhenViewReady = false;
  private quoteStateVersion = 0;

  @ViewChild('uploadForm') uploadForm!: UploadFormComponent;
  @ViewChild('resultCol') resultCol!: ElementRef;

  constructor(
    private estimator: QuoteEstimatorService,
    private router: Router,
    private route: ActivatedRoute,
    private languageService: LanguageService,
    private translate: TranslateService,
    @Optional() @Inject(PLATFORM_ID) platformId?: Object,
  ) {
    this.isBrowser = isPlatformBrowser(platformId ?? 'browser');
  }

  ngOnInit() {
    this.route.data.subscribe((data) => {
      if (data['mode']) {
        this.mode.set(data['mode']);
      }
    });

    if (!this.isBrowser) {
      return;
    }

    this.route.queryParams.subscribe((params) => {
      const sessionId = params['session'];
      if (sessionId) {
        // Avoid reloading if we just calculated this session
        const currentRes = this.result();
        if (!currentRes || currentRes.sessionId !== sessionId) {
          this.loadSession(sessionId);
        } else {
          this.clearQuoteErrorState();
          this.applyPendingSessionRestoreIfNeeded();
        }
      }
    });
  }

  ngAfterViewInit() {
    this.applyPendingSessionRestoreIfNeeded();

    if (this.currentSessionId()) {
      // Restore the in-memory files immediately when switching between the
      // basic and advanced routes. The server refresh may replace this state
      // afterwards, but the file cards and 3D preview never disappear.
      this.restorePendingDraftFallback(false);
      return;
    }
    const pendingDraft = this.estimator.consumePendingCalculatorDraft();
    if (!pendingDraft) return;

    this.uploadForm?.restoreRequestDraft(pendingDraft.request, {
      sameSettingsForAll: pendingDraft.sameSettingsForAll,
      selectedFileName: pendingDraft.selectedFileName,
      previewFiles: pendingDraft.previewFiles,
    });
  }

  modeContentKey(field: string): string {
    const modeKey = this.mode() === 'easy' ? 'BASIC' : 'ADVANCED';
    return `CALC.MODES.${modeKey}.${field}`;
  }

  modelSourceDescriptionKey(id: string): string {
    return `CALC.MODEL_SOURCES.ITEMS.${id}`;
  }

  faqKey(id: string, field: string): string {
    return `CALC.FAQ.ITEMS.${id}.${field}`;
  }

  loadSession(sessionId: string) {
    this.quoteStateVersion += 1;
    this.pendingSessionRestore = null;
    this.loading.set(true);
    this.estimator.getQuoteSession(sessionId).subscribe({
      next: (data) => {
        // 1. Map to Result
        const result = this.estimator.mapSessionToQuoteResult(data);
        if (this.isInvalidQuote(result)) {
          const failure = result.failedItems?.[0] ?? {
            fileName: data.items?.[0]?.originalFilename || '',
            code: 'MODEL_PROCESSING_FAILED',
            message: '',
          };
          this.setQuoteError(
            'CALC.ERROR_GENERIC',
            this.failureDisplayMessage(failure),
            failure.code || null,
          );
          this.restoreFilesAndSettings(data.session, data.items || []);
          return;
        }

        this.clearQuoteErrorState();
        this.warningMessage.set(
          this.buildPartialFailureMessage(result.failedItems || []),
        );
        this.result.set(result);
        this.baselinePrintSettings = this.toTrackedSettingsFromSession(
          data.session,
        );
        this.baselineItemStates = this.buildBaselineItemStatesFromSession(
          data.items || [],
          this.baselinePrintSettings,
        );
        this.requiresRecalculation.set(false);
        this.itemSettingsDiffByFileName.set({});
        const isCadSession = data?.session?.status === 'CAD_ACTIVE';
        this.cadSessionLocked.set(isCadSession);
        this.step.set('quote');

        // 2. Determine Mode (Heuristic)
        // If we have custom settings, maybe Advanced?
        // For now, let's stick to current mode or infer from URL if possible.
        // Actually, we can check if settings deviate from Easy defaults.
        // But let's leave it as is or default to Advanced if not sure.
        // data.session.materialCode etc.

        // 3. Download Files & Restore Form
        this.restoreFilesAndSettings(data.session, data.items);
      },
      error: (err) => {
        console.error('Failed to load session', err);
        this.setQuoteError('CALC.ERROR_GENERIC');
        this.loading.set(false);
      },
    });
  }

  restoreFilesAndSettings(session: any, items: any[]) {
    const restoreStateVersion = this.quoteStateVersion;
    if (!items || items.length === 0) {
      this.restoreDraftWhenViewReady = true;
      this.restorePendingDraftFallback();
      this.loading.set(false);
      return;
    }

    // File restore is client-only: SSR can render the quote summary, but binary
    // session content must be fetched fresh in the browser to avoid transfer
    // cache serializing blobs into unusable objects during hydration.
    if (!this.isBrowser) {
      this.loading.set(false);
      return;
    }

    // Download all files
    const downloads = items.map((item) =>
      forkJoin({
        originalBlob: this.estimator.getLineItemContent(session.id, item.id),
        previewBlob: item?.convertedStoredPath
          ? this.estimator
              .getLineItemContent(session.id, item.id, true)
              .pipe(catchError(() => of(null)))
          : of(null),
      }).pipe(
        map(({ originalBlob, previewBlob }) => {
          return {
            originalBlob,
            previewBlob,
            fileName: item.originalFilename,
            hasConvertedPreview: !!item.convertedStoredPath,
          };
        }),
      ),
    );

    forkJoin(downloads).subscribe({
      next: (results: any[]) => {
        if (
          restoreStateVersion !== this.quoteStateVersion ||
          !this.isCurrentSessionRestore(session)
        ) {
          if (
            restoreStateVersion !== this.quoteStateVersion &&
            this.isCurrentSessionRestore(session)
          ) {
            this.loading.set(false);
          }
          return;
        }

        const files = results.map(
          (res) =>
            new File([res.originalBlob], res.fileName, {
              type: 'application/octet-stream',
            }),
        );

        const previewFiles = results.flatMap((res, index) => {
          if (!res.hasConvertedPreview || !res.previewBlob) {
            return [];
          }

          const previewName = res.fileName
            .replace(/\.[^.]+$/, '')
            .concat('.stl');
          const previewFile = new File([res.previewBlob], previewName, {
            type: 'model/stl',
          });
          return [{ index, file: previewFile }];
        });

        this.pendingSessionRestore = {
          session,
          items,
          files,
          previewFiles,
          preserveError: this.error() && !this.result(),
        };
        this.applyPendingSessionRestoreIfNeeded();
        this.loading.set(false);
      },
      error: (err: any) => {
        if (restoreStateVersion !== this.quoteStateVersion) {
          if (this.isCurrentSessionRestore(session)) {
            this.loading.set(false);
          }
          return;
        }
        console.error('Failed to download files', err);
        this.restoreDraftWhenViewReady = true;
        this.restorePendingDraftFallback();
        this.loading.set(false);
        // Still show result? Yes.
      },
    });
  }

  onCalculate(req: QuoteRequest) {
    // ... (logic remains the same, simplified for diff)
    this.quoteStateVersion += 1;
    this.pendingSessionRestore = null;
    this.currentRequest = req;
    this.estimator.setPendingCalculatorDraft({
      request: req,
      sameSettingsForAll: this.uploadForm.sameSettingsForAll(),
      selectedFileName: this.uploadForm.selectedFile()?.name ?? null,
      previewFiles: this.uploadForm.getPreviewFilesByIndex(),
    });
    this.loading.set(true);
    this.uploadProgress.set(0);
    this.clearQuoteErrorState();
    this.warningMessage.set(null);
    this.result.set(null);
    this.cadSessionLocked.set(false);
    this.orderSuccess.set(false);

    // Auto-scroll on mobile to make analysis visible
    setTimeout(() => {
      if (this.isBrowser && this.resultCol && window.innerWidth < 768) {
        this.resultCol.nativeElement.scrollIntoView({
          behavior: 'smooth',
          block: 'start',
        });
      }
    }, 100);

    this.estimator.calculate(req).subscribe({
      next: (event) => {
        if (typeof event === 'number') {
          this.uploadProgress.set(event);
        } else {
          // It's the result
          const res = event as QuoteResult;
          if (this.isInvalidQuote(res)) {
            const failure = res.failedItems?.[0] ?? {
              fileName: req.items[0]?.file.name || '',
              code: 'MODEL_PROCESSING_FAILED',
              message: '',
            };
            this.setQuoteError(
              'CALC.ERROR_GENERIC',
              this.failureDisplayMessage(failure),
              failure.code,
            );
            this.applyFailureStates([failure]);
            this.loading.set(false);
            return;
          }

          this.clearQuoteErrorState();
          this.warningMessage.set(
            this.buildPartialFailureMessage(res.failedItems || []),
          );
          this.applyFailureStates(res.failedItems || []);
          this.result.set(res);
          this.baselinePrintSettings = this.toTrackedSettingsFromRequest(req);
          this.baselineItemStates =
            this.buildBaselineItemStatesFromRequest(req);
          this.requiresRecalculation.set(false);
          this.itemSettingsDiffByFileName.set({});
          this.loading.set(false);
          this.uploadProgress.set(100);
          this.step.set('quote');

          // Update URL with session ID without reloading
          if (res.sessionId) {
            this.router.navigate([], {
              relativeTo: this.route,
              queryParams: { session: res.sessionId },
              queryParamsHandling: 'merge', // merge with existing params like 'mode' if any
              replaceUrl: true, // prevent cluttering history, or false if we want back button to work. replaceUrl seems safer for "state update"
            });
            this.estimator.getQuoteSession(res.sessionId).subscribe({
              next: (sessionData) => {
                this.restoreFilesAndSettings(
                  sessionData.session,
                  sessionData.items || [],
                );
              },
              error: (err) => {
                console.warn('Failed to refresh files for preview', err);
              },
            });
          }
        }
      },
      error: (err) => {
        const failure = this.normalizeCalculationFailure(err) ?? {
          fileName: req.items[0]?.file.name || '',
          code: 'MODEL_PROCESSING_FAILED',
          message: '',
        };
        if (failure.sessionId) {
          this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { session: failure.sessionId },
            queryParamsHandling: 'merge',
            replaceUrl: true,
          });
        }
        this.setQuoteError(
          failure.code === 'QUOTE_RATE_LIMITED' || failure.status === 429
            ? 'CALC.ERROR_RATE_LIMIT'
            : 'CALC.ERROR_GENERIC',
          this.failureDisplayMessage(failure),
          failure.code || null,
        );
        this.applyFailureStates([failure]);
        this.loading.set(false);
      },
    });
  }

  onProceed() {
    const res = this.result();
    if (res && res.sessionId) {
      this.persistPendingDraft();
      const segments = this.cadSessionLocked()
        ? ['/', this.languageService.selectedLang(), 'checkout', 'cad']
        : ['/', this.languageService.selectedLang(), 'checkout'];
      this.router.navigate(segments, {
        queryParams: { session: res.sessionId },
      });
    } else {
      console.error('No session ID found in quote result');
      // Fallback or error handling
    }
  }

  onCancelDetails() {
    this.step.set('quote');
  }

  onItemChange(event: {
    id?: string;
    index: number;
    fileName: string;
    quantity: number;
    source?: 'left' | 'right';
  }) {
    // 1. Update local form for consistency (UI feedback)
    if (event.source !== 'left' && this.uploadForm) {
      this.uploadForm.updateItemQuantityByIndex(event.index, event.quantity);
      this.uploadForm.updateItemQuantityByName(event.fileName, event.quantity);
    }

    // 2. Update backend session if ID exists
    if (event.id) {
      const currentSessionId = this.result()?.sessionId;
      if (!currentSessionId) return;

      this.estimator
        .updateLineItem(event.id, { quantity: event.quantity })
        .subscribe({
          next: () => {
            // 3. Fetch the updated session totals from the backend
            this.estimator.getQuoteSession(currentSessionId).subscribe({
              next: (sessionData) => {
                const newResult =
                  this.estimator.mapSessionToQuoteResult(sessionData);
                // Preserve notes
                newResult.notes = this.result()?.notes;

                if (this.isInvalidQuote(newResult)) {
                  this.setQuoteError('CALC.ERROR_ZERO_PRICE');
                  return;
                }

                this.clearQuoteErrorState();
                this.result.set(newResult);
              },
              error: (err) => {
                console.error('Failed to refresh session totals', err);
              },
            });
          },
          error: (err) => {
            console.error('Failed to update line item', err);
          },
        });
    }
  }

  onUploadItemQuantityChange(event: {
    index: number;
    fileName: string;
    quantity: number;
  }) {
    const resultItems = this.result()?.items || [];
    const byIndex = resultItems[event.index];
    const byName = resultItems.find((item) => item.fileName === event.fileName);
    const id = byIndex?.id ?? byName?.id;

    this.onItemChange({
      ...event,
      id,
      source: 'left',
    });
  }

  onQuoteItemQuantityPreviewChange(event: {
    index: number;
    fileName: string;
    quantity: number;
  }) {
    if (!this.uploadForm) return;
    this.uploadForm.updateItemQuantityByIndex(event.index, event.quantity);
    this.uploadForm.updateItemQuantityByName(event.fileName, event.quantity);
  }

  onSubmitOrder(orderData: any) {
    console.log('Order Submitted:', orderData);
    this.orderSuccess.set(true);
    this.step.set('success');
  }

  onNewQuote() {
    this.quoteStateVersion += 1;
    this.pendingSessionRestore = null;
    this.step.set('upload');
    this.result.set(null);
    this.requiresRecalculation.set(false);
    this.itemSettingsDiffByFileName.set({});
    this.baselinePrintSettings = null;
    this.baselineItemStates = [];
    this.cadSessionLocked.set(false);
    this.orderSuccess.set(false);
    this.switchMode('easy'); // Reset to default and sync URL
  }

  private currentRequest: QuoteRequest | null = null;

  onUploadPrintSettingsChange(_: TrackedPrintSettings) {
    void _;
    if (this.isRestoringQuoteState) return;
    if (!this.result()) return;
    this.quoteStateVersion += 1;
    this.pendingSessionRestore = null;
    this.refreshRecalculationRequirement();
  }

  onItemSettingsDiffChange(
    diffByFileName: Record<string, { differences: string[] }>,
  ) {
    this.itemSettingsDiffByFileName.set(diffByFileName || {});
  }

  onConsult() {
    const currentFormRequest = this.uploadForm?.getCurrentRequestDraft();
    const req = currentFormRequest ?? this.currentRequest;

    if (!req) {
      this.router.navigate([
        '/',
        this.languageService.selectedLang(),
        'contact',
      ]);
      return;
    }

    let details = `${this.translate.instant('CALC.CONSULTATION.TITLE')}:\n`;
    details += `- ${this.translate.instant('CALC.CONSULTATION.MATERIAL')}: ${req.material}\n`;
    details += `- ${this.translate.instant('CALC.CONSULTATION.QUALITY')}: ${this.localizedQuality(req.quality)}\n`;

    details += `- ${this.translate.instant('CALC.CONSULTATION.FILES')}:\n`;
    req.items.forEach((item) => {
      details += `  * ${item.file.name} (${this.translate.instant('CALC.CONSULTATION.QUANTITY')}: ${item.quantity}`;
      if (item.color) {
        details += `, ${this.translate.instant('CALC.CONSULTATION.COLOR')}: ${this.localizedColor(item.color)}`;
      }
      details += `)\n`;
    });

    if (req.mode === 'advanced') {
      if (req.infillDensity) {
        details += `- ${this.translate.instant('CALC.CONSULTATION.INFILL')}: ${req.infillDensity}%\n`;
      }
    }
    const requiresManualReview =
      this.errorCode() === 'MODEL_OUT_OF_PRINT_VOLUME' ||
      this.errorCode() === 'MODEL_PROCESSING_FAILED' ||
      (this.result()?.failedItems || []).some(
        (failure) =>
          failure.code === 'MODEL_OUT_OF_PRINT_VOLUME' ||
          failure.code === 'MODEL_PROCESSING_FAILED',
      );
    if (requiresManualReview) {
      details += `- ${this.translate.instant('CALC.CONSULTATION.MANUAL_REVIEW')}\n`;
    }

    if (req.notes) {
      details += `\n${this.translate.instant('CALC.CONSULTATION.NOTES')}: ${req.notes}`;
    }

    this.estimator.setPendingConsultation({
      files: req.items.map((i) => i.file),
      message: details,
    });

    this.router.navigate(['/', this.languageService.selectedLang(), 'contact']);
  }

  private isInvalidQuote(result: QuoteResult): boolean {
    const invalidPrice =
      !Number.isFinite(result.totalPrice) || result.totalPrice <= 0;
    const invalidWeight =
      !Number.isFinite(result.totalWeight) || result.totalWeight <= 0;
    const invalidTime =
      !Number.isFinite(result.totalTimeHours) ||
      !Number.isFinite(result.totalTimeMinutes) ||
      (result.totalTimeHours <= 0 && result.totalTimeMinutes <= 0);

    return invalidPrice || invalidWeight || invalidTime;
  }

  private setQuoteError(
    key: string,
    message: string | null = null,
    code: string | null = null,
  ): void {
    this.quoteStateVersion += 1;
    this.pendingSessionRestore = null;
    this.errorKey.set(key);
    this.errorMessage.set(message);
    this.errorCode.set(code);
    this.warningMessage.set(null);
    this.error.set(true);
    this.result.set(null);
    this.requiresRecalculation.set(false);
    this.itemSettingsDiffByFileName.set({});
    this.baselinePrintSettings = null;
    this.baselineItemStates = [];
  }

  private clearQuoteErrorState(): void {
    this.error.set(false);
    this.errorKey.set('CALC.ERROR_GENERIC');
    this.errorMessage.set(null);
    this.errorCode.set(null);
  }

  private normalizeCalculationFailure(
    error: unknown,
  ): QuoteCalculationFailure | null {
    if (!error || typeof error !== 'object') {
      return null;
    }

    const maybeFailure = error as Partial<QuoteCalculationFailure>;
    const hasMessage =
      typeof maybeFailure.message === 'string' &&
      maybeFailure.message.trim().length > 0;
    const hasCode = typeof maybeFailure.code === 'string';
    const hasStatus = typeof maybeFailure.status === 'number';
    if (hasMessage || hasCode || hasStatus) {
      return {
        fileName:
          typeof maybeFailure.fileName === 'string'
            ? maybeFailure.fileName
            : '',
        sessionId:
          typeof maybeFailure.sessionId === 'string'
            ? maybeFailure.sessionId
            : undefined,
        status:
          typeof maybeFailure.status === 'number'
            ? maybeFailure.status
            : undefined,
        code:
          typeof maybeFailure.code === 'string' ? maybeFailure.code : undefined,
        message: hasMessage ? maybeFailure.message!.trim() : '',
      };
    }

    return null;
  }

  private buildPartialFailureMessage(
    failures: QuoteCalculationFailure[],
  ): string | null {
    if (!failures.length) {
      return null;
    }

    if (failures.length === 1) {
      const failure = failures[0];
      return this.translate.instant('CALC.REVIEW_PARTIAL_SINGLE', {
        fileName: failure.fileName,
        reason: this.failureDisplayMessage(failure),
      });
    }

    const fileNames = failures
      .map((failure) => failure.fileName)
      .filter((fileName) => fileName.trim().length > 0);
    const sharedMessage = failures.every(
      (failure) => failure.message === failures[0].message,
    )
      ? failures[0].message
      : null;

    return this.translate.instant('CALC.REVIEW_PARTIAL_MULTIPLE', {
      count: failures.length,
      fileNames: fileNames.join(', '),
      reason: sharedMessage
        ? this.failureDisplayMessage({ ...failures[0], message: sharedMessage })
        : '',
    });
  }

  private failureDisplayMessage(failure: QuoteCalculationFailure): string {
    if (failure.code === 'QUOTE_RATE_LIMITED' || failure.status === 429) {
      return this.translate.instant('CALC.ERROR_RATE_LIMIT');
    }
    if (failure.code === 'MODEL_OUT_OF_PRINT_VOLUME') {
      return this.translate.instant('CALC.REVIEW_OUT_OF_VOLUME');
    }
    if (failure.code === 'MODEL_PROCESSING_FAILED') {
      return this.translate.instant('CALC.REVIEW_PROCESSING_FAILED');
    }
    if (failure.code === 'MODEL_REQUIRES_CUSTOM_QUOTE') {
      return this.translate.instant('CALC.CUSTOM_QUOTE_HELP');
    }
    if (failure.code === 'QUOTE_SESSION_INIT_FAILED') {
      return this.translate.instant('CALC.ERROR_SESSION_INIT');
    }
    if (failure.code === 'QUOTE_FINALIZATION_FAILED') {
      return this.translate.instant('CALC.ERROR_FINALIZATION');
    }
    if (failure.code === 'QUOTE_ITEM_PROCESSING_FAILED') {
      return this.translate.instant('CALC.ERROR_ITEM_PROCESSING');
    }
    return failure.message || this.translate.instant('CALC.ERROR_GENERIC');
  }

  private localizedQuality(value: string): string {
    const key = `CALC.QUALITY_OPTIONS.${String(value || '').toUpperCase()}`;
    const translated = this.translate.instant(key);
    return translated === key ? value : translated;
  }

  private localizedColor(value: string): string {
    const colorKey = String(value || '')
      .trim()
      .replace(/[-\s]+/g, '_')
      .toUpperCase();
    const key = `COLOR.NAME.${colorKey}`;
    const translated = this.translate.instant(key);
    return translated === key ? value : translated;
  }

  switchMode(nextMode: 'easy' | 'advanced'): void {
    if (this.cadSessionLocked()) return;

    const targetPath = nextMode === 'easy' ? 'basic' : 'advanced';
    const currentPath = this.route.snapshot?.routeConfig?.path;

    this.mode.set(nextMode);

    if (currentPath === targetPath) {
      return;
    }

    this.persistPendingDraft();

    this.router.navigate(['..', targetPath], {
      relativeTo: this.route,
      queryParamsHandling: 'preserve',
    });
  }

  private currentSessionId(): string | null {
    const fromResult = this.result()?.sessionId;
    if (fromResult) {
      return fromResult;
    }

    const snapshot = this.route.snapshot;
    const fromQueryParamMap = snapshot?.queryParamMap?.get?.('session');
    if (fromQueryParamMap) {
      return fromQueryParamMap;
    }

    const fromQueryParams = snapshot?.queryParams?.['session'];
    return typeof fromQueryParams === 'string' && fromQueryParams.length > 0
      ? fromQueryParams
      : null;
  }

  private persistPendingDraft(): void {
    if (!this.uploadForm) {
      this.estimator.setPendingCalculatorDraft(null);
      return;
    }

    const request = this.uploadForm.getCurrentRequestDraft();
    if (!request.items.length) {
      this.estimator.setPendingCalculatorDraft(null);
      return;
    }

    const draft: PendingCalculatorDraft = {
      request,
      sameSettingsForAll: this.uploadForm.sameSettingsForAll(),
      selectedFileName: this.uploadForm.selectedFile()?.name ?? null,
      previewFiles: this.uploadForm.getPreviewFilesByIndex(),
    };
    this.estimator.setPendingCalculatorDraft(draft);
  }

  private applyPendingSessionRestoreIfNeeded(): void {
    if (!this.uploadForm || !this.pendingSessionRestore) {
      return;
    }

    const payload = this.pendingSessionRestore;
    if (!this.isCurrentSessionRestore(payload.session)) {
      this.pendingSessionRestore = null;
      return;
    }

    const baselineSessionSettings = this.toTrackedSettingsFromSession(
      payload.session,
    );
    const localDraft = this.estimator.getPendingCalculatorDraft();
    const selectedFileName = this.normalizeFileName(
      localDraft?.selectedFileName ??
        this.uploadForm.selectedFile()?.name ??
        '',
    );

    this.isRestoringQuoteState = true;
    try {
      if (localDraft?.request.items.length) {
        this.uploadForm.restoreRequestDraft(localDraft.request, {
          sameSettingsForAll: localDraft.sameSettingsForAll,
          selectedFileName: localDraft.selectedFileName,
          previewFiles: localDraft.previewFiles,
        });
      } else {
        this.uploadForm.setFiles(payload.files, { autoSelect: false });
        payload.previewFiles.forEach((preview) => {
          this.uploadForm.setPreviewFileByIndex(preview.index, preview.file);
        });
        this.uploadForm.patchSettings(payload.session);
      }

      payload.items.forEach((item, index) => {
        if (localDraft?.request.items.length) {
          if (item.status === 'REVIEW_REQUIRED') {
            const failure: QuoteCalculationFailure = {
              fileName: item.originalFilename || '',
              code: item.errorCode,
              message: item.errorMessage || '',
            };
            this.uploadForm.setItemReviewStateByName(
              item.originalFilename || '',
              item.errorCode === 'MODEL_OUT_OF_PRINT_VOLUME'
                ? 'warning'
                : 'error',
              this.failureDisplayMessage(failure),
            );
          }
          return;
        }

        // Preserve persisted quantities when restoring from session.
        // Without this, setFiles() defaults every item back to 1.
        this.uploadForm.updateItemQuantityByIndex(
          index,
          Number(item.quantity || 1),
        );

        const tracked = this.toTrackedSettingsFromSessionItem(
          item,
          baselineSessionSettings,
        );
        this.uploadForm.setItemPrintSettingsByIndex(index, {
          material: tracked.material.toUpperCase(),
          quality: tracked.quality,
          nozzleDiameter: tracked.nozzleDiameter,
          layerHeight: tracked.layerHeight,
          infillDensity: tracked.infillDensity,
          infillPattern: tracked.infillPattern,
          supportEnabled: tracked.supportEnabled,
        });

        if (item.colorCode) {
          this.uploadForm.updateItemColor(index, {
            colorName: item.colorCode,
            filamentVariantId: item.filamentVariantId,
          });
        }
        if (item.status === 'REVIEW_REQUIRED') {
          const failure: QuoteCalculationFailure = {
            fileName: item.originalFilename || '',
            code: item.errorCode,
            message: item.errorMessage || '',
          };
          this.uploadForm.setItemReviewStateByIndex(
            index,
            item.errorCode === 'MODEL_OUT_OF_PRINT_VOLUME'
              ? 'warning'
              : 'error',
            this.failureDisplayMessage(failure),
          );
        }
      });

      this.uploadForm.setAcceptSplitPrinting(
        payload.items.some(
          (item) =>
            item.status !== 'REVIEW_REQUIRED' &&
            Boolean(item.requiresSplitPrinting),
        ),
      );

      if (!localDraft?.request.items.length) {
        const selected =
          payload.files.find(
            (file) => this.normalizeFileName(file.name) === selectedFileName,
          ) ??
          payload.files[payload.files.length - 1] ??
          null;
        if (selected) {
          this.uploadForm.selectFile(selected);
        }
      }
    } finally {
      this.isRestoringQuoteState = false;
    }

    this.baselinePrintSettings = baselineSessionSettings;
    this.baselineItemStates = this.buildBaselineItemStatesFromSession(
      payload.items || [],
      baselineSessionSettings,
    );
    this.requiresRecalculation.set(false);
    this.refreshRecalculationRequirement();
    if (!payload.preserveError) {
      this.clearQuoteErrorState();
    }
    this.estimator.setPendingCalculatorDraft(null);
    this.restoreDraftWhenViewReady = false;
    this.pendingSessionRestore = null;
  }

  private restorePendingDraftFallback(consume = true): void {
    if (!this.uploadForm) return;
    const pendingDraft = consume
      ? this.estimator.consumePendingCalculatorDraft()
      : this.estimator.getPendingCalculatorDraft();
    if (!pendingDraft) return;
    this.uploadForm.restoreRequestDraft(pendingDraft.request, {
      sameSettingsForAll: pendingDraft.sameSettingsForAll,
      selectedFileName: pendingDraft.selectedFileName,
      previewFiles: pendingDraft.previewFiles,
    });
    this.restoreDraftWhenViewReady = false;
  }

  private applyFailureStates(failures: QuoteCalculationFailure[]): void {
    if (!this.uploadForm) return;
    failures.forEach((failure) => {
      this.uploadForm.setItemReviewStateByName(
        failure.fileName,
        failure.code === 'MODEL_OUT_OF_PRINT_VOLUME' ? 'warning' : 'error',
        this.failureDisplayMessage(failure),
      );
    });
  }

  private toTrackedSettingsFromRequest(
    req: QuoteRequest,
  ): TrackedPrintSettings {
    return {
      mode: req.mode,
      material: this.normalizeString(req.material || 'PLA'),
      quality: this.normalizeString(req.quality || 'standard'),
      nozzleDiameter: this.normalizeNumber(req.nozzleDiameter, 0.4, 2),
      layerHeight: this.normalizeNumber(req.layerHeight, 0.2, 3),
      infillDensity: this.normalizeNumber(req.infillDensity, 20, 2),
      infillPattern: this.normalizeString(req.infillPattern || 'grid'),
      supportEnabled: Boolean(req.supportEnabled),
    };
  }

  private toTrackedSettingsFromItem(
    req: QuoteRequest,
    item: QuoteRequest['items'][number],
  ): TrackedPrintSettings {
    return {
      mode: req.mode,
      material: this.normalizeString(item.material || req.material || 'PLA'),
      quality: this.normalizeString(item.quality || req.quality || 'standard'),
      nozzleDiameter: this.normalizeNumber(
        item.nozzleDiameter ?? req.nozzleDiameter,
        0.4,
        2,
      ),
      layerHeight: this.normalizeNumber(
        item.layerHeight ?? req.layerHeight,
        0.2,
        3,
      ),
      infillDensity: this.normalizeNumber(
        item.infillDensity ?? req.infillDensity,
        20,
        2,
      ),
      infillPattern: this.normalizeString(
        item.infillPattern || req.infillPattern || 'grid',
      ),
      supportEnabled: Boolean(item.supportEnabled ?? req.supportEnabled),
    };
  }

  private toTrackedSettingsFromSession(session: any): TrackedPrintSettings {
    const layer = this.normalizeNumber(session?.layerHeightMm, 0.2, 3);
    return {
      mode: this.mode(),
      material: this.normalizeString(session?.materialCode || 'PLA'),
      quality:
        layer >= 0.24 ? 'draft' : layer <= 0.12 ? 'extra_fine' : 'standard',
      nozzleDiameter: this.normalizeNumber(session?.nozzleDiameterMm, 0.4, 2),
      layerHeight: layer,
      infillDensity: this.normalizeNumber(session?.infillPercent, 20, 2),
      infillPattern: this.normalizeString(session?.infillPattern || 'grid'),
      supportEnabled: Boolean(session?.supportsEnabled),
    };
  }

  private toTrackedSettingsFromSessionItem(
    item: any,
    fallback: TrackedPrintSettings,
  ): TrackedPrintSettings {
    const layer = this.normalizeNumber(
      item?.layerHeightMm,
      fallback.layerHeight,
      3,
    );
    return {
      mode: this.mode(),
      material: this.normalizeString(item?.materialCode || fallback.material),
      quality: this.normalizeString(
        item?.quality ||
          (layer >= 0.24 ? 'draft' : layer <= 0.12 ? 'extra_fine' : 'standard'),
      ),
      nozzleDiameter: this.normalizeNumber(
        item?.nozzleDiameterMm,
        fallback.nozzleDiameter,
        2,
      ),
      layerHeight: layer,
      infillDensity: this.normalizeNumber(
        item?.infillPercent,
        fallback.infillDensity,
        2,
      ),
      infillPattern: this.normalizeString(
        item?.infillPattern || fallback.infillPattern,
      ),
      supportEnabled: Boolean(item?.supportsEnabled ?? fallback.supportEnabled),
    };
  }

  private buildBaselineItemStatesFromRequest(
    req: QuoteRequest,
  ): TrackedPrintItemState[] {
    return req.items.map((item) => ({
      fileName: this.normalizeFileName(item.file?.name || ''),
      settings: this.toTrackedSettingsFromItem(req, item),
    }));
  }

  private buildBaselineItemStatesFromSession(
    items: any[],
    defaultSettings: TrackedPrintSettings | null,
  ): TrackedPrintItemState[] {
    const fallback = defaultSettings ?? this.defaultTrackedSettings();
    return items.map((item) => ({
      fileName: this.normalizeFileName(item?.originalFilename || ''),
      settings: this.toTrackedSettingsFromSessionItem(item, fallback),
    }));
  }

  private defaultTrackedSettings(): TrackedPrintSettings {
    return {
      mode: this.mode(),
      material: 'pla',
      quality: 'standard',
      nozzleDiameter: 0.4,
      layerHeight: 0.2,
      infillDensity: 20,
      infillPattern: 'grid',
      supportEnabled: false,
    };
  }

  private refreshRecalculationRequirement(): void {
    if (!this.result()) return;

    const draft = this.uploadForm?.getCurrentRequestDraft();
    if (!draft || draft.items.length === 0) {
      this.requiresRecalculation.set(false);
      return;
    }

    const fallback = this.baselinePrintSettings;
    if (!fallback) {
      this.requiresRecalculation.set(false);
      return;
    }

    if (this.baselineItemStates.length === 0) {
      this.requiresRecalculation.set(false);
      return;
    }

    const currentItemStates = draft.items.map((item) => ({
      fileName: this.normalizeFileName(item.file?.name || ''),
      settings: this.toTrackedSettingsFromItem(draft, item),
    }));

    const changed = !this.sameTrackedItemStates(
      this.baselineItemStates,
      currentItemStates,
    );

    this.requiresRecalculation.set(changed);
  }

  private sameTrackedItemStates(
    baseline: TrackedPrintItemState[],
    current: TrackedPrintItemState[],
  ): boolean {
    if (baseline.length !== current.length) {
      return false;
    }

    const counts = new Map<string, number>();
    baseline.forEach((item) => {
      const signature = this.trackedItemSignature(item);
      counts.set(signature, (counts.get(signature) || 0) + 1);
    });

    for (const item of current) {
      const signature = this.trackedItemSignature(item);
      const count = counts.get(signature) || 0;
      if (count <= 0) {
        return false;
      }

      if (count === 1) {
        counts.delete(signature);
      } else {
        counts.set(signature, count - 1);
      }
    }

    return counts.size === 0;
  }

  private trackedItemSignature(item: TrackedPrintItemState): string {
    const settings = item.settings;
    return [
      item.fileName,
      settings.mode,
      settings.material,
      settings.quality,
      settings.nozzleDiameter.toFixed(4),
      settings.layerHeight.toFixed(4),
      settings.infillDensity.toFixed(4),
      settings.infillPattern,
      settings.supportEnabled ? '1' : '0',
    ].join('\u0001');
  }

  private normalizeFileName(fileName: string): string {
    return (fileName || '').split(/[\\/]/).pop()?.trim().toLowerCase() ?? '';
  }

  private normalizeString(value: string): string {
    return String(value || '')
      .trim()
      .toLowerCase();
  }

  private normalizeNumber(
    value: unknown,
    fallback: number,
    decimals: number,
  ): number {
    const numeric = Number(value);
    const resolved = Number.isFinite(numeric) ? numeric : fallback;
    const factor = 10 ** decimals;
    return Math.round(resolved * factor) / factor;
  }

  private isCurrentSessionRestore(session: any): boolean {
    const restoreSessionId = String(session?.id || '');
    const currentResultSessionId = String(this.currentSessionId() || '');
    return (
      restoreSessionId.length > 0 &&
      currentResultSessionId.length > 0 &&
      restoreSessionId === currentResultSessionId
    );
  }
}
