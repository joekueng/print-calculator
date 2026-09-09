import { Injectable, inject, signal } from '@angular/core';
import {
  HttpClient,
  HttpErrorResponse,
  HttpEventType,
  HttpResponse,
} from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface QuoteRequestItem {
  file: File;
  quantity: number;
  material?: string;
  quality?: string;
  color?: string;
  filamentVariantId?: number;
  supportEnabled?: boolean;
  infillDensity?: number;
  infillPattern?: string;
  layerHeight?: number;
  nozzleDiameter?: number;
}

export interface QuoteRequest {
  items: QuoteRequestItem[];
  material: string;
  quality: string;
  notes?: string;
  acceptSplitPrinting?: boolean;
  infillDensity?: number;
  infillPattern?: string;
  supportEnabled?: boolean;
  layerHeight?: number;
  nozzleDiameter?: number;
  mode: 'easy' | 'advanced';
}

export interface PendingCalculatorDraft {
  request: QuoteRequest;
  sameSettingsForAll: boolean;
  selectedFileName?: string | null;
  previewFiles?: Array<File | null>;
}

export interface QuoteItem {
  id?: string;
  fileName: string;
  unitPrice: number;
  unitTime: number;
  unitWeight: number;
  quantity: number;
  material?: string;
  quality?: string;
  color?: string;
  filamentVariantId?: number;
  supportEnabled?: boolean;
  infillDensity?: number;
  infillPattern?: string;
  layerHeight?: number;
  nozzleDiameter?: number;
  requiresSplitPrinting?: boolean;
}

export interface QuoteCalculationFailure {
  fileName: string;
  sessionId?: string;
  status?: number;
  code?: string;
  message: string;
}

export interface QuoteResult {
  sessionId?: string;
  items: QuoteItem[];
  baseSetupCost?: number;
  nozzleChangeCost?: number;
  setupCost: number;
  globalMachineCost: number;
  cadHours?: number;
  cadTotal?: number;
  currency: string;
  totalPrice: number;
  totalTimeHours: number;
  totalTimeMinutes: number;
  totalWeight: number;
  notes?: string;
  failedItems?: QuoteCalculationFailure[];
}

export interface MaterialOption {
  code: string;
  label: string;
  isTechnical: boolean;
  variants: VariantOption[];
}

export interface VariantOption {
  id: number;
  name: string;
  colorName: string;
  colorLabelIt?: string;
  colorLabelEn?: string;
  colorLabelDe?: string;
  colorLabelFr?: string;
  hexColor: string;
  finishType: string;
  stockSpools: number;
  stockFilamentGrams: number;
  isOutOfStock: boolean;
}

export interface QualityOption {
  id: string;
  label: string;
}

export interface InfillOption {
  id: string;
  label: string;
}

export interface NumericOption {
  value: number;
  label: string;
}

export interface NozzleLayerHeightOptions {
  nozzleDiameter: number;
  layerHeights: NumericOption[];
}

export interface OptionsResponse {
  materials: MaterialOption[];
  qualities: QualityOption[];
  infillPatterns: InfillOption[];
  layerHeights: NumericOption[];
  nozzleDiameters: NumericOption[];
  layerHeightsByNozzle: NozzleLayerHeightOptions[];
}

export interface SimpleOption {
  value: string | number;
  label: string;
}

@Injectable({
  providedIn: 'root',
})
export class QuoteEstimatorService {
  private http = inject(HttpClient);

  private pendingConsultation = signal<{
    files: File[];
    message: string;
  } | null>(null);
  private pendingCalculatorDraft = signal<PendingCalculatorDraft | null>(null);

  getOptions(): Observable<OptionsResponse> {
    const headers: any = {};
    return this.http.get<OptionsResponse>(
      `${environment.apiUrl}/api/calculator/options`,
      {
        headers,
      },
    );
  }

  getQuoteSession(sessionId: string): Observable<any> {
    const headers: any = {};
    return this.http.get(
      `${environment.apiUrl}/api/quote-sessions/${sessionId}`,
      {
        headers,
      },
    );
  }

  updateLineItem(lineItemId: string, changes: any): Observable<any> {
    const headers: any = {};
    return this.http.patch(
      `${environment.apiUrl}/api/quote-sessions/line-items/${lineItemId}`,
      changes,
      { headers },
    );
  }

  createOrder(sessionId: string, orderDetails: any): Observable<any> {
    const headers: any = {};
    return this.http.post(
      `${environment.apiUrl}/api/orders/from-quote/${sessionId}`,
      orderDetails,
      { headers },
    );
  }

  getOrder(orderId: string): Observable<any> {
    const headers: any = {};
    return this.http.get(`${environment.apiUrl}/api/orders/${orderId}`, {
      headers,
    });
  }

  reportPayment(orderId: string, method: string): Observable<any> {
    const headers: any = {};
    return this.http.post(
      `${environment.apiUrl}/api/orders/${orderId}/payments/report`,
      { method },
      { headers },
    );
  }

  getOrderInvoice(orderId: string): Observable<Blob> {
    const headers: any = {};
    return this.http.get(
      `${environment.apiUrl}/api/orders/${orderId}/invoice`,
      {
        headers,
        responseType: 'blob',
      },
    );
  }

  getOrderConfirmation(orderId: string): Observable<Blob> {
    const headers: any = {};
    return this.http.get(
      `${environment.apiUrl}/api/orders/${orderId}/confirmation`,
      {
        headers,
        responseType: 'blob',
      },
    );
  }

  getOrderCadFiles(orderId: string): Observable<HttpResponse<Blob>> {
    const headers: any = {};
    return this.http.get(
      `${environment.apiUrl}/api/orders/${orderId}/cad-files/download`,
      {
        headers,
        responseType: 'blob',
        observe: 'response',
      },
    );
  }

  getTwintPayment(orderId: string): Observable<any> {
    const headers: any = {};
    return this.http.get(`${environment.apiUrl}/api/orders/${orderId}/twint`, {
      headers,
    });
  }

  calculate(request: QuoteRequest): Observable<number | QuoteResult> {
    if (!request.items || request.items.length === 0) {
      return of(0);
    }

    return new Observable<number | QuoteResult>((observer) => {
      const headers: any = {};

      this.http
        .post<any>(`${environment.apiUrl}/api/quote-sessions`, {}, { headers })
        .subscribe({
          next: (sessionRes) => {
            const sessionId = String(sessionRes?.id || '');
            if (!sessionId) {
              observer.error({
                fileName: request.items[0]?.file.name || '',
                code: 'QUOTE_SESSION_INIT_FAILED',
                message: '',
              } satisfies QuoteCalculationFailure);
              return;
            }

            const totalItems = request.items.length;
            const uploadProgress = new Array(totalItems).fill(0);
            const uploadResults: {
              success: boolean;
              failure?: QuoteCalculationFailure;
            }[] = new Array(totalItems).fill(null).map(() => ({
              success: false,
            }));
            let completed = 0;

            const emitProgress = () => {
              const avg = Math.round(
                uploadProgress.reduce((sum, value) => sum + value, 0) /
                  totalItems,
              );
              observer.next(avg);
            };

            const finalize = () => {
              emitProgress();
              if (completed !== totalItems) {
                return;
              }

              const successfulUploads = uploadResults.filter(
                (entry) => entry.success,
              ).length;
              const failures = uploadResults
                .map((entry) => entry.failure)
                .filter(
                  (failure): failure is QuoteCalculationFailure => !!failure,
                );

              if (successfulUploads === 0) {
                observer.error(
                  failures[0] || {
                    fileName: request.items[0]?.file.name || '',
                    code: 'QUOTE_ITEM_PROCESSING_FAILED',
                    message: '',
                  },
                );
                return;
              }

              this.getQuoteSession(sessionId).subscribe({
                next: (sessionData) => {
                  observer.next(100);
                  const result = this.mapSessionToQuoteResult(sessionData);
                  result.notes = request.notes;
                  result.failedItems = failures;
                  observer.next(result);
                  observer.complete();
                },
                error: () => {
                  observer.error({
                    fileName: request.items[0]?.file.name || '',
                    sessionId,
                    code: 'QUOTE_FINALIZATION_FAILED',
                    message: '',
                  } satisfies QuoteCalculationFailure);
                },
              });
            };

            request.items.forEach((item, index) => {
              const formData = new FormData();
              formData.append('file', item.file);

              const settings = this.buildSettingsPayload(request, item);
              const settingsBlob = new Blob([JSON.stringify(settings)], {
                type: 'application/json',
              });
              formData.append('settings', settingsBlob);

              this.http
                .post<any>(
                  `${environment.apiUrl}/api/quote-sessions/${sessionId}/line-items`,
                  formData,
                  {
                    headers,
                    reportProgress: true,
                    observe: 'events',
                  },
                )
                .subscribe({
                  next: (event) => {
                    if (
                      event.type === HttpEventType.UploadProgress &&
                      event.total
                    ) {
                      uploadProgress[index] = Math.round(
                        (100 * event.loaded) / event.total,
                      );
                      emitProgress();
                      return;
                    }

                    if (event.type === HttpEventType.Response) {
                      uploadProgress[index] = 100;
                      const responseItem = event.body;
                      const success = responseItem?.status === 'READY';
                      uploadResults[index] = success
                        ? { success: true }
                        : {
                            success: false,
                            failure: {
                              fileName:
                                responseItem?.originalFilename ||
                                item.file.name,
                              sessionId,
                              code:
                                responseItem?.pricingBreakdown?.errorCode ||
                                'QUOTE_ITEM_PROCESSING_FAILED',
                              message:
                                responseItem?.errorMessage ||
                                '',
                            },
                          };
                      completed += 1;
                      finalize();
                    }
                  },
                  error: (error) => {
                    uploadProgress[index] = 100;
                    uploadResults[index] = {
                      success: false,
                      failure: {
                        ...this.normalizeCalculationFailure(
                          error,
                          item.file.name,
                        ),
                        sessionId,
                      },
                    };
                    completed += 1;
                    finalize();
                  },
                });
            });
          },
          error: (error) => {
            observer.error(
              this.normalizeCalculationFailure(
                error,
                request.items[0]?.file.name || '',
              ),
            );
          },
        });
    });
  }

  setPendingConsultation(data: { files: File[]; message: string }) {
    this.pendingConsultation.set(data);
  }

  getPendingConsultation() {
    const data = this.pendingConsultation();
    this.pendingConsultation.set(null);
    return data;
  }

  setPendingCalculatorDraft(data: PendingCalculatorDraft | null) {
    this.pendingCalculatorDraft.set(data);
  }

  getPendingCalculatorDraft(): PendingCalculatorDraft | null {
    return this.pendingCalculatorDraft();
  }

  consumePendingCalculatorDraft(): PendingCalculatorDraft | null {
    const data = this.pendingCalculatorDraft();
    this.pendingCalculatorDraft.set(null);
    return data;
  }

  getLineItemContent(
    sessionId: string,
    lineItemId: string,
    preview = false,
  ): Observable<Blob> {
    const headers: any = {};
    const previewQuery = preview ? '?preview=true' : '';
    return this.http.get(
      `${environment.apiUrl}/api/quote-sessions/${sessionId}/line-items/${lineItemId}/content${previewQuery}`,
      {
        headers,
        responseType: 'blob',
        transferCache: false,
      },
    );
  }

  getLineItemStlPreview(
    sessionId: string,
    lineItemId: string,
  ): Observable<Blob> {
    const headers: any = {};
    return this.http.get(
      `${environment.apiUrl}/api/quote-sessions/${sessionId}/line-items/${lineItemId}/stl-preview`,
      {
        headers,
        responseType: 'blob',
        transferCache: false,
      },
    );
  }

  private normalizeCalculationFailure(
    error: unknown,
    fileName: string,
  ): QuoteCalculationFailure {
    if (error instanceof HttpErrorResponse) {
      const isRateLimited = error.status === 429;
      const body = error.error;
      if (body && typeof body === 'object' && !(body instanceof Blob)) {
        const payload = body as Record<string, unknown>;
        const message =
          typeof payload['message'] === 'string' &&
          payload['message'].trim().length > 0
            ? payload['message'].trim()
            : '';

        return {
          fileName,
          status: error.status || undefined,
          code: isRateLimited
            ? 'QUOTE_RATE_LIMITED'
            : typeof payload['code'] === 'string'
              ? payload['code']
              : 'QUOTE_ITEM_PROCESSING_FAILED',
          message,
        };
      }

      return {
        fileName,
        status: error.status || undefined,
        code: isRateLimited
          ? 'QUOTE_RATE_LIMITED'
          : 'QUOTE_ITEM_PROCESSING_FAILED',
        message: '',
      };
    }

    if (typeof error === 'string' && error.trim().length > 0) {
      return {
        fileName,
        message: error.trim(),
      };
    }

    return {
      fileName,
      code: 'QUOTE_ITEM_PROCESSING_FAILED',
      message: '',
    };
  }

  mapSessionToQuoteResult(sessionData: any): QuoteResult {
    const session = sessionData?.session || {};
    const allItems = Array.isArray(sessionData?.items) ? sessionData.items : [];
    const items = allItems.filter(
      (item: any) => item?.status !== 'REVIEW_REQUIRED',
    );
    const failedItems: QuoteCalculationFailure[] = allItems
      .filter((item: any) => item?.status === 'REVIEW_REQUIRED')
      .map((item: any) => ({
        fileName: item?.originalFilename || '',
        sessionId: session?.id,
        code: item?.errorCode || 'QUOTE_ITEM_PROCESSING_FAILED',
        message: item?.errorMessage || '',
      }));

    const totalTime = items.reduce(
      (acc: number, item: any) =>
        acc + Number(item?.printTimeSeconds || 0) * Number(item?.quantity || 1),
      0,
    );

    const totalWeight = items.reduce(
      (acc: number, item: any) =>
        acc + Number(item?.materialGrams || 0) * Number(item?.quantity || 1),
      0,
    );

    const grandTotal = Number(sessionData?.grandTotalChf);
    const effectiveSetupCost = Number(
      sessionData?.setupCostChf ?? session?.setupCostChf ?? 0,
    );
    const fallbackTotal =
      Number(sessionData?.itemsTotalChf || 0) +
      effectiveSetupCost +
      Number(sessionData?.shippingCostChf || 0);

    return {
      sessionId: session?.id,
      items: items.map((item: any) => ({
        id: item?.id,
        fileName: item?.originalFilename,
        unitPrice: Number(item?.unitPriceChf || 0),
        unitTime: Number(item?.printTimeSeconds || 0),
        unitWeight: Number(item?.materialGrams || 0),
        quantity: Number(item?.quantity || 1),
        material: item?.materialCode || session?.materialCode,
        quality: item?.quality,
        color: item?.colorCode,
        filamentVariantId: item?.filamentVariantId,
        supportEnabled: Boolean(item?.supportsEnabled),
        infillDensity:
          item?.infillPercent != null ? Number(item.infillPercent) : undefined,
        infillPattern: item?.infillPattern,
        layerHeight:
          item?.layerHeightMm != null ? Number(item.layerHeightMm) : undefined,
        nozzleDiameter:
          item?.nozzleDiameterMm != null
            ? Number(item.nozzleDiameterMm)
            : undefined,
        requiresSplitPrinting: Boolean(item?.requiresSplitPrinting),
      })),
      baseSetupCost: Number(
        sessionData?.baseSetupCostChf ?? session?.setupCostChf ?? 0,
      ),
      nozzleChangeCost: Number(sessionData?.nozzleChangeCostChf ?? 0),
      setupCost: effectiveSetupCost,
      globalMachineCost: Number(sessionData?.globalMachineCostChf || 0),
      cadHours: Number(session?.cadHours || 0),
      cadTotal: Number(sessionData?.cadTotalChf || 0),
      currency: 'CHF',
      totalPrice: Number.isFinite(grandTotal) ? grandTotal : fallbackTotal,
      totalTimeHours: Math.floor(totalTime / 3600),
      totalTimeMinutes: Math.ceil((totalTime % 3600) / 60),
      totalWeight: Math.ceil(totalWeight),
      notes: session?.notes,
      failedItems,
    };
  }

  private buildSettingsPayload(
    request: QuoteRequest,
    item: QuoteRequestItem,
  ): any {
    const normalizedQuality = this.normalizeQuality(
      item.quality || request.quality,
    );
    const easyPreset =
      request.mode === 'easy'
        ? this.buildEasyModePreset(normalizedQuality)
        : null;

    return {
      complexityMode: request.mode === 'easy' ? 'BASIC' : 'ADVANCED',
      quantity: this.normalizeQuantity(item.quantity),
      material: String(item.material || request.material || 'PLA'),
      color: item.color || '#FFFFFF',
      filamentVariantId: item.filamentVariantId,
      quality: easyPreset ? easyPreset.quality : normalizedQuality,
      supportsEnabled: item.supportEnabled ?? request.supportEnabled ?? false,
      layerHeight:
        easyPreset?.layerHeight ??
        item.layerHeight ??
        request.layerHeight ??
        0.2,
      infillDensity:
        easyPreset?.infillDensity ??
        item.infillDensity ??
        request.infillDensity ??
        20,
      infillPattern:
        easyPreset?.infillPattern ??
        item.infillPattern ??
        request.infillPattern ??
        'grid',
      nozzleDiameter:
        easyPreset?.nozzleDiameter ??
        item.nozzleDiameter ??
        request.nozzleDiameter ??
        0.4,
      allowSplitForOversized: request.acceptSplitPrinting === true,
    };
  }

  private normalizeQuantity(value: number | undefined): number {
    const numeric = Number(value);
    if (!Number.isFinite(numeric) || numeric < 1) {
      return 1;
    }
    return Math.floor(numeric);
  }

  private normalizeQuality(value: string | undefined): string {
    const normalized = String(value || 'standard')
      .trim()
      .toLowerCase();
    if (normalized === 'high' || normalized === 'high_definition') {
      return 'extra_fine';
    }
    return normalized || 'standard';
  }

  private buildEasyModePreset(quality: string): {
    quality: string;
    layerHeight: number;
    infillDensity: number;
    infillPattern: string;
    nozzleDiameter: number;
  } {
    const normalized = this.normalizeQuality(quality);

    if (normalized === 'draft') {
      return {
        quality: 'draft',
        layerHeight: 0.28,
        infillDensity: 15,
        infillPattern: 'grid',
        nozzleDiameter: 0.4,
      };
    }

    if (normalized === 'extra_fine') {
      return {
        quality: 'extra_fine',
        layerHeight: 0.12,
        infillDensity: 20,
        infillPattern: 'gyroid',
        nozzleDiameter: 0.4,
      };
    }

    return {
      quality: 'standard',
      layerHeight: 0.2,
      infillDensity: 15,
      infillPattern: 'grid',
      nozzleDiameter: 0.4,
    };
  }
}
