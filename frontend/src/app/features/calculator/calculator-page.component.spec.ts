import { of, Subject, throwError } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { CalculatorPageComponent } from './calculator-page.component';
import {
  PendingCalculatorDraft,
  QuoteEstimatorService,
  QuoteRequest,
  QuoteResult,
} from './services/quote-estimator.service';
import { LanguageService } from '../../core/services/language.service';
import { UploadFormComponent } from './components/upload-form/upload-form.component';
import { TranslateService } from '@ngx-translate/core';

describe('CalculatorPageComponent', () => {
  const createResult = (sessionId: string, notes?: string): QuoteResult => ({
    sessionId,
    items: [
      {
        id: 'line-1',
        fileName: 'part-a.stl',
        unitPrice: 4,
        unitTime: 120,
        unitWeight: 2,
        quantity: 1,
      },
    ],
    setupCost: 2,
    globalMachineCost: 0,
    currency: 'CHF',
    totalPrice: 6,
    totalTimeHours: 0,
    totalTimeMinutes: 2,
    totalWeight: 2,
    notes,
  });

  const createDraftRequest = (): QuoteRequest => ({
    items: [
      {
        file: new File(['mesh'], 'part-a.stl', { type: 'model/stl' }),
        quantity: 2,
        material: 'PLA',
        quality: 'standard',
        color: 'Black',
        supportEnabled: true,
        infillDensity: 15,
        infillPattern: 'grid',
        layerHeight: 0.2,
        nozzleDiameter: 0.4,
      },
    ],
    material: 'PLA',
    quality: 'standard',
    notes: 'draft note',
    infillDensity: 15,
    infillPattern: 'grid',
    supportEnabled: true,
    layerHeight: 0.2,
    nozzleDiameter: 0.4,
    mode: 'easy',
  });

  function createComponent(
    platformId?: Object,
    queryParams: Record<string, unknown> = {},
  ) {
    TestBed.resetTestingModule();

    const estimator = jasmine.createSpyObj<QuoteEstimatorService>(
      'QuoteEstimatorService',
      [
        'updateLineItem',
        'getQuoteSession',
        'getLineItemContent',
        'mapSessionToQuoteResult',
        'calculate',
        'setPendingCalculatorDraft',
        'getPendingCalculatorDraft',
        'consumePendingCalculatorDraft',
      ],
    );
    estimator.getPendingCalculatorDraft.and.returnValue(null);
    const router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    const route = {
      data: of({}),
      queryParams: of(queryParams),
      snapshot: {
        routeConfig: { path: 'basic' },
        queryParams,
        queryParamMap: {
          get: (key: string) => {
            const value = queryParams[key];
            return typeof value === 'string' ? value : null;
          },
        },
      },
    } as unknown as ActivatedRoute;
    const languageService = jasmine.createSpyObj<LanguageService>(
      'LanguageService',
      ['selectedLang'],
    );
    const translate = jasmine.createSpyObj<TranslateService>(
      'TranslateService',
      ['instant'],
    );
    translate.instant.and.callFake(
      (key: string, params?: Record<string, unknown>) => {
        if (key === 'CALC.REVIEW_PARTIAL_SINGLE') {
          return `${params?.['fileName']} was not included in the quote. ${params?.['reason']}`;
        }
        if (key === 'CALC.REVIEW_OUT_OF_VOLUME') {
          return 'This model could not be placed fully inside the printer volume.';
        }
        return key;
      },
    );

    const component = new CalculatorPageComponent(
      estimator,
      router,
      route,
      languageService,
      translate,
      platformId,
    );

    const uploadForm = jasmine.createSpyObj<UploadFormComponent>(
      'UploadFormComponent',
      [
        'setFiles',
        'setPreviewFileByIndex',
        'setItemReviewStateByIndex',
        'setItemReviewStateByName',
        'setAcceptSplitPrinting',
        'patchSettings',
        'setItemPrintSettingsByIndex',
        'updateItemColor',
        'selectFile',
        'updateItemQuantityByIndex',
        'updateItemQuantityByName',
        'getCurrentRequestDraft',
        'getPreviewFilesByIndex',
        'restoreRequestDraft',
      ],
    );
    uploadForm.sameSettingsForAll = jasmine
      .createSpy('sameSettingsForAll')
      .and.returnValue(true) as any;
    uploadForm.selectedFile = jasmine
      .createSpy('selectedFile')
      .and.returnValue(null) as any;
    uploadForm.getPreviewFilesByIndex.and.returnValue([]);
    component.uploadForm = uploadForm;

    return {
      component,
      estimator,
      route,
      uploadForm,
    };
  }

  it('updates left panel quantities even when item id is missing', () => {
    const { component, estimator, uploadForm } = createComponent();

    component.onItemChange({
      index: 0,
      fileName: 'part-a.stl',
      quantity: 4,
    });

    expect(uploadForm.updateItemQuantityByIndex).toHaveBeenCalledWith(0, 4);
    expect(uploadForm.updateItemQuantityByName).toHaveBeenCalledWith(
      'part-a.stl',
      4,
    );
    expect(estimator.updateLineItem).not.toHaveBeenCalled();
  });

  it('refreshes quote totals after successful line item update', () => {
    const { component, estimator } = createComponent();
    component.result.set(createResult('session-1', 'persisted notes'));

    estimator.updateLineItem.and.returnValue(of({ ok: true }));
    estimator.getQuoteSession.and.returnValue(
      of({ session: { id: 'session-1' } }),
    );
    estimator.mapSessionToQuoteResult.and.returnValue(
      createResult('session-1'),
    );

    component.onItemChange({
      id: 'line-1',
      index: 0,
      fileName: 'part-a.stl',
      quantity: 7,
    });

    expect(estimator.updateLineItem).toHaveBeenCalledWith('line-1', {
      quantity: 7,
    });
    expect(estimator.getQuoteSession).toHaveBeenCalledWith('session-1');
    expect(component.result()?.notes).toBe('persisted notes');
    expect(component.result()?.items[0].quantity).toBe(1);
  });

  it('builds mode-specific content keys', () => {
    const { component } = createComponent();

    component.mode.set('easy');
    expect(component.modeContentKey('TITLE')).toBe('CALC.MODES.BASIC.TITLE');

    component.mode.set('advanced');
    expect(component.modeContentKey('TITLE')).toBe('CALC.MODES.ADVANCED.TITLE');
  });

  it('exposes the expected external model sources and faq entries', () => {
    const { component } = createComponent();

    expect(component.favoriteModelSources.map((entry) => entry.id)).toEqual([
      'PRINTABLES',
      'MAKERWORLD',
    ]);
    expect(component.otherModelSources.map((entry) => entry.id)).toEqual([
      'THINGIVERSE',
      'THANGS',
      'CULTS3D',
      'YEGGI',
    ]);
    expect(component.modelSources.map((entry) => entry.id)).toEqual([
      'PRINTABLES',
      'MAKERWORLD',
      'THINGIVERSE',
      'THANGS',
      'CULTS3D',
      'YEGGI',
    ]);
    expect(component.faqIds).toEqual([
      'FILES',
      'MODE',
      'NO_MODEL',
      'PRICE',
      'BEFORE_UPLOAD',
    ]);
  });

  it('stores the current draft before switching mode without a session', () => {
    const { component, estimator, uploadForm } = createComponent();
    const draftRequest = createDraftRequest();
    uploadForm.getCurrentRequestDraft.and.returnValue(draftRequest);
    (uploadForm.sameSettingsForAll as jasmine.Spy).and.returnValue(false);
    (uploadForm.selectedFile as jasmine.Spy).and.returnValue(
      draftRequest.items[0].file,
    );

    component.switchMode('advanced');

    expect(estimator.setPendingCalculatorDraft).toHaveBeenCalledWith({
      request: draftRequest,
      sameSettingsForAll: false,
      selectedFileName: 'part-a.stl',
      previewFiles: [],
    });
  });

  it('stores files and previews before switching mode with a quote session', () => {
    const { component, estimator, uploadForm } = createComponent(undefined, {
      session: 'session-1',
    });
    const draftRequest = createDraftRequest();
    const previewFile = new File(['preview'], 'part-a-preview.stl', {
      type: 'model/stl',
    });
    uploadForm.getCurrentRequestDraft.and.returnValue(draftRequest);
    uploadForm.getPreviewFilesByIndex.and.returnValue([previewFile]);
    (uploadForm.sameSettingsForAll as jasmine.Spy).and.returnValue(false);
    (uploadForm.selectedFile as jasmine.Spy).and.returnValue(
      draftRequest.items[0].file,
    );

    component.switchMode('advanced');

    expect(estimator.setPendingCalculatorDraft).toHaveBeenCalledWith({
      request: draftRequest,
      sameSettingsForAll: false,
      selectedFileName: 'part-a.stl',
      previewFiles: [previewFile],
    });
  });

  it('restores a pending draft after view init when there is no session', () => {
    const { component, estimator, uploadForm } = createComponent();
    const draftRequest = createDraftRequest();
    const pendingDraft: PendingCalculatorDraft = {
      request: draftRequest,
      sameSettingsForAll: true,
      selectedFileName: 'part-a.stl',
    };
    estimator.consumePendingCalculatorDraft.and.returnValue(pendingDraft);

    component.ngAfterViewInit();

    expect(uploadForm.restoreRequestDraft).toHaveBeenCalledWith(draftRequest, {
      sameSettingsForAll: true,
      selectedFileName: 'part-a.stl',
      previewFiles: undefined,
    });
  });

  it('restores files and previews after a mode switch with a quote session', () => {
    const { component, estimator, uploadForm } = createComponent(undefined, {
      session: 'session-1',
    });
    const draftRequest = createDraftRequest();
    const previewFile = new File(['preview'], 'part-a-preview.stl', {
      type: 'model/stl',
    });
    estimator.getPendingCalculatorDraft.and.returnValue({
      request: draftRequest,
      sameSettingsForAll: false,
      selectedFileName: 'part-a.stl',
      previewFiles: [previewFile],
    });

    component.ngAfterViewInit();

    expect(uploadForm.restoreRequestDraft).toHaveBeenCalledWith(draftRequest, {
      sameSettingsForAll: false,
      selectedFileName: 'part-a.stl',
      previewFiles: [previewFile],
    });
  });

  it('shows partial quote results when some files fail', () => {
    const { component, estimator } = createComponent();
    const request = createDraftRequest();
    const result = createResult('session-1');
    result.failedItems = [
      {
        fileName: 'cube-256.stl',
        code: 'MODEL_OUT_OF_PRINT_VOLUME',
        message:
          'This model could not be placed fully inside the printer volume.',
      },
    ];

    estimator.calculate.and.returnValue(of(result));
    estimator.getQuoteSession.and.returnValue(
      of({ session: { id: 'session-1' }, items: [] }),
    );

    component.onCalculate(request);

    expect(component.error()).toBeFalse();
    expect(component.result()?.sessionId).toBe('session-1');
    expect(component.warningMessage()).toContain('cube-256.stl');
    expect(component.warningMessage()).toContain(
      'This model could not be placed fully inside the printer volume',
    );
  });

  it('shows backend failure message when calculation fails completely', () => {
    const { component, estimator, uploadForm } = createComponent();
    const request = createDraftRequest();

    estimator.calculate.and.returnValue(
      throwError(() => ({
        fileName: 'cube-257.stl',
        code: 'MODEL_OUT_OF_PRINT_VOLUME',
        message:
          'This model could not be placed fully inside the printer volume.',
      })),
    );

    component.onCalculate(request);

    expect(component.error()).toBeTrue();
    expect(component.errorMessage()).toBe(
      'This model could not be placed fully inside the printer volume.',
    );
    expect(component.isCustomQuoteError()).toBeTrue();
    expect(uploadForm.setItemReviewStateByName).toHaveBeenCalledWith(
      'cube-257.stl',
      'warning',
      'This model could not be placed fully inside the printer volume.',
    );
  });

  it('restores the local draft when a session has no downloadable items', () => {
    const { component, estimator, uploadForm } = createComponent(undefined, {
      session: 'session-1',
    });
    const request = createDraftRequest();
    estimator.consumePendingCalculatorDraft.and.returnValue({
      request,
      sameSettingsForAll: true,
      selectedFileName: 'part-a.stl',
    });

    component.restoreFilesAndSettings({ id: 'session-1' }, []);

    expect(uploadForm.restoreRequestDraft).toHaveBeenCalledWith(request, {
      sameSettingsForAll: true,
      selectedFileName: 'part-a.stl',
      previewFiles: undefined,
    });
  });

  it('marks custom quote failures for the custom quote CTA state', () => {
    const { component, estimator } = createComponent();
    const request = createDraftRequest();

    estimator.calculate.and.returnValue(
      throwError(() => ({
        fileName: 'large-part.stl',
        code: 'MODEL_REQUIRES_CUSTOM_QUOTE',
        message:
          'This model is too large for the automatic split-printing estimate. Please request a custom quote.',
      })),
    );

    component.onCalculate(request);

    expect(component.error()).toBeTrue();
    expect(component.isCustomQuoteError()).toBeTrue();
    expect(component.errorCode()).toBe('MODEL_REQUIRES_CUSTOM_QUOTE');
  });

  it('keeps the local file list authoritative during a session refresh', () => {
    const { component, estimator, uploadForm } = createComponent();
    const draftRequest = createDraftRequest();
    component.result.set(createResult('session-1'));
    estimator.getPendingCalculatorDraft.and.returnValue({
      request: draftRequest,
      sameSettingsForAll: true,
      selectedFileName: 'part-a.stl',
      previewFiles: [draftRequest.items[0].file],
    });
    estimator.getLineItemContent.and.returnValue(of(new Blob(['server'])));

    component.restoreFilesAndSettings(
      { id: 'session-1' },
      [{ id: 'line-1', originalFilename: 'part-a.stl', quantity: 1 }],
    );

    expect(uploadForm.restoreRequestDraft).toHaveBeenCalled();
    expect(uploadForm.setFiles).not.toHaveBeenCalled();
    expect(estimator.setPendingCalculatorDraft).toHaveBeenCalledWith(null);
  });

  it('downloads converted previews only for items that expose them', () => {
    const { component, estimator, uploadForm } = createComponent();
    const originalBlob = new Blob(['original']);
    const previewBlob = new Blob(['preview']);
    component.result.set(createResult('session-1'));

    estimator.getLineItemContent.and.callFake(
      (_sessionId: string, _lineItemId: string, preview = false) =>
        of(preview ? previewBlob : originalBlob),
    );
    (uploadForm.selectedFile as jasmine.Spy).and.returnValue(
      new File(['current'], 'legacy.stl', { type: 'model/stl' }),
    );

    component.restoreFilesAndSettings(
      {
        id: 'session-1',
        materialCode: 'PLA',
        layerHeightMm: 0.2,
        nozzleDiameterMm: 0.4,
        infillPercent: 15,
        infillPattern: 'grid',
        supportsEnabled: true,
      },
      [
        {
          id: 'line-stl',
          originalFilename: 'legacy.stl',
          quantity: 1,
        },
        {
          id: 'line-3mf',
          originalFilename: 'converted.3mf',
          quantity: 1,
          convertedStoredPath: 'storage_quotes/session-1/converted.stl',
        },
      ],
    );

    expect(estimator.getLineItemContent.calls.allArgs()).toEqual([
      ['session-1', 'line-stl'],
      ['session-1', 'line-3mf'],
      ['session-1', 'line-3mf', true],
    ]);
    expect(uploadForm.setPreviewFileByIndex).toHaveBeenCalledTimes(1);
    expect(uploadForm.setPreviewFileByIndex).toHaveBeenCalledWith(
      1,
      jasmine.any(File),
    );
    expect(uploadForm.selectFile.calls.mostRecent().args[0].name).toBe(
      'legacy.stl',
    );
  });

  it('skips binary session restore during SSR', () => {
    const { component, estimator, uploadForm } = createComponent('server');

    component.restoreFilesAndSettings(
      {
        id: 'session-1',
      },
      [
        {
          id: 'line-stl',
          originalFilename: 'legacy.stl',
          quantity: 1,
        },
      ],
    );

    expect(estimator.getLineItemContent).not.toHaveBeenCalled();
    expect(uploadForm.setFiles).not.toHaveBeenCalled();
    expect(component.loading()).toBeFalse();
  });

  it('does not restore a quote session from query params during SSR', () => {
    const { component, estimator } = createComponent('server', {
      session: 'session-1',
    });

    component.ngOnInit();

    expect(estimator.getQuoteSession).not.toHaveBeenCalled();
  });

  it('restores a quote session from query params in the browser', () => {
    const { component, estimator } = createComponent('browser', {
      session: 'session-1',
    });

    estimator.getQuoteSession.and.returnValue(
      of({
        session: {
          id: 'session-1',
          status: 'ACTIVE',
          materialCode: 'PLA',
          quality: 'standard',
          nozzleDiameterMm: 0.4,
          layerHeightMm: 0.2,
          infillPercent: 15,
          infillPattern: 'grid',
          supportsEnabled: true,
        },
        items: [],
      }),
    );
    estimator.mapSessionToQuoteResult.and.returnValue(
      createResult('session-1'),
    );

    component.ngOnInit();

    expect(estimator.getQuoteSession).toHaveBeenCalledWith('session-1');
    expect(component.result()?.sessionId).toBe('session-1');
    expect(component.loading()).toBeFalse();
  });

  it('applies a pending session restore after the upload form becomes available', () => {
    const { component, estimator } = createComponent();
    const originalBlob = new Blob(['original']);
    const previewBlob = new Blob(['preview']);

    component.uploadForm = undefined as unknown as UploadFormComponent;
    component.result.set(createResult('session-1'));
    component.error.set(true);

    estimator.getLineItemContent.and.callFake(
      (_sessionId: string, _lineItemId: string, preview = false) =>
        of(preview ? previewBlob : originalBlob),
    );

    component.restoreFilesAndSettings(
      {
        id: 'session-1',
        materialCode: 'PLA',
        layerHeightMm: 0.2,
        nozzleDiameterMm: 0.4,
        infillPercent: 15,
        infillPattern: 'grid',
        supportsEnabled: true,
      },
      [
        {
          id: 'line-3mf',
          originalFilename: 'converted.3mf',
          quantity: 2,
          colorCode: 'White',
          filamentVariantId: 7,
          materialCode: 'PLA',
          quality: 'standard',
          nozzleDiameterMm: 0.4,
          layerHeightMm: 0.2,
          infillPercent: 15,
          infillPattern: 'grid',
          supportsEnabled: true,
          convertedStoredPath: 'storage_quotes/session-1/converted.stl',
        },
      ],
    );

    const uploadForm = jasmine.createSpyObj<UploadFormComponent>(
      'UploadFormComponent',
      [
        'setFiles',
        'setPreviewFileByIndex',
        'setItemReviewStateByIndex',
        'setItemReviewStateByName',
        'setAcceptSplitPrinting',
        'patchSettings',
        'setItemPrintSettingsByIndex',
        'updateItemColor',
        'selectFile',
        'updateItemQuantityByIndex',
        'updateItemQuantityByName',
        'getCurrentRequestDraft',
        'getPreviewFilesByIndex',
        'restoreRequestDraft',
      ],
    );
    uploadForm.sameSettingsForAll = jasmine
      .createSpy('sameSettingsForAll')
      .and.returnValue(true) as any;
    uploadForm.selectedFile = jasmine
      .createSpy('selectedFile')
      .and.returnValue(null) as any;
    uploadForm.getPreviewFilesByIndex.and.returnValue([]);

    component.uploadForm = uploadForm;
    component.ngAfterViewInit();

    expect(uploadForm.setFiles).toHaveBeenCalledTimes(1);
    expect(uploadForm.setFiles).toHaveBeenCalledWith(jasmine.any(Array), {
      autoSelect: false,
    });
    expect(uploadForm.setPreviewFileByIndex).toHaveBeenCalledWith(
      0,
      jasmine.any(File),
    );
    expect(uploadForm.patchSettings).toHaveBeenCalled();
    expect(uploadForm.updateItemQuantityByIndex).toHaveBeenCalledWith(0, 2);
    expect(uploadForm.updateItemColor).toHaveBeenCalledWith(0, {
      colorName: 'White',
      filamentVariantId: 7,
    });
    expect(uploadForm.selectFile).toHaveBeenCalledWith(jasmine.any(File));
    expect(component.error()).toBeFalse();
  });

  it('does not keep recalculation required after restoring the recalculated session', () => {
    const { component, estimator, uploadForm } = createComponent();
    const originalBlob = new Blob(['original']);
    component.mode.set('advanced');
    component.result.set(createResult('session-1'));

    const trackedLayer01 = {
      mode: 'advanced',
      material: 'pla',
      quality: 'standard',
      nozzleDiameter: 0.4,
      layerHeight: 0.1,
      infillDensity: 15,
      infillPattern: 'grid',
      supportEnabled: true,
    } as const;
    component['baselinePrintSettings'] = trackedLayer01;
    component['baselineItemStates'] = [
      { fileName: 'part-a.stl', settings: trackedLayer01 },
    ];

    const requestWithLayer = (layerHeight: number): QuoteRequest => {
      const request = createDraftRequest();
      return {
        ...request,
        mode: 'advanced',
        layerHeight,
        items: request.items.map((item) => ({ ...item, layerHeight })),
      };
    };
    let currentDraft = requestWithLayer(0.2);

    estimator.getLineItemContent.and.returnValue(of(originalBlob));
    uploadForm.getCurrentRequestDraft.and.callFake(() => currentDraft);
    uploadForm.patchSettings.and.callFake(() => {
      component.onUploadPrintSettingsChange({
        mode: 'advanced',
        material: 'PLA',
        quality: 'standard',
        nozzleDiameter: 0.4,
        layerHeight: 0.2,
        infillDensity: 15,
        infillPattern: 'grid',
        supportEnabled: true,
      });
      currentDraft = requestWithLayer(0.1);
    });

    component.restoreFilesAndSettings(
      {
        id: 'session-1',
        materialCode: 'PLA',
        quality: 'standard',
        nozzleDiameterMm: 0.4,
        layerHeightMm: 0.1,
        infillPercent: 15,
        infillPattern: 'grid',
        supportsEnabled: true,
      },
      [
        {
          id: 'line-1',
          originalFilename: 'part-a.stl',
          quantity: 1,
          materialCode: 'PLA',
          quality: 'standard',
          nozzleDiameterMm: 0.4,
          layerHeightMm: 0.1,
          infillPercent: 15,
          infillPattern: 'grid',
          supportsEnabled: true,
        },
      ],
    );

    expect(component.requiresRecalculation()).toBeFalse();
  });

  it('does not require recalculation for duplicate file names with different materials', () => {
    const { component, uploadForm } = createComponent();
    component.mode.set('advanced');
    component.result.set(createResult('session-1'));

    const globalSettings = {
      mode: 'advanced',
      material: 'pla',
      quality: 'extra_fine',
      nozzleDiameter: 0.2,
      layerHeight: 0.08,
      infillDensity: 100,
      infillPattern: 'grid',
      supportEnabled: false,
    } as const;
    const asaSettings = {
      ...globalSettings,
      material: 'asa',
      nozzleDiameter: 0.4,
      layerHeight: 0.2,
    };
    const plaSettings = {
      ...globalSettings,
      material: 'pla',
      nozzleDiameter: 0.2,
      layerHeight: 0.08,
    };

    component['baselinePrintSettings'] = globalSettings;
    component['baselineItemStates'] = [
      { fileName: 'duplicate.stl', settings: asaSettings },
      { fileName: 'duplicate.stl', settings: plaSettings },
    ];

    const duplicateAsa = new File(['asa'], 'duplicate.stl', {
      type: 'model/stl',
    });
    const duplicatePla = new File(['pla'], 'duplicate.stl', {
      type: 'model/stl',
    });

    const calculatedDraft: QuoteRequest = {
      items: [
        {
          file: duplicatePla,
          quantity: 1,
          material: 'PLA',
          quality: 'extra_fine',
          supportEnabled: false,
          infillDensity: 100,
          infillPattern: 'grid',
          layerHeight: 0.08,
          nozzleDiameter: 0.2,
        },
        {
          file: duplicateAsa,
          quantity: 1,
          material: 'ASA',
          quality: 'extra_fine',
          supportEnabled: false,
          infillDensity: 100,
          infillPattern: 'grid',
          layerHeight: 0.2,
          nozzleDiameter: 0.4,
        },
      ],
      material: 'PLA',
      quality: 'extra_fine',
      infillDensity: 100,
      infillPattern: 'grid',
      supportEnabled: false,
      layerHeight: 0.08,
      nozzleDiameter: 0.2,
      mode: 'advanced',
    };

    uploadForm.getCurrentRequestDraft.and.returnValue(calculatedDraft);

    component.onUploadPrintSettingsChange(globalSettings);

    expect(component.requiresRecalculation()).toBeFalse();

    uploadForm.getCurrentRequestDraft.and.returnValue({
      ...calculatedDraft,
      items: calculatedDraft.items.map((item, index) =>
        index === 1 ? { ...item, material: 'PLA' } : item,
      ),
    });

    component.onUploadPrintSettingsChange(globalSettings);

    expect(component.requiresRecalculation()).toBeTrue();
  });

  it('ignores stale file restores after the active quote session changes', () => {
    const { component, estimator, uploadForm } = createComponent();
    const staleDownload = new Subject<Blob>();
    component.result.set(createResult('session-old'));
    component.loading.set(true);

    estimator.getLineItemContent.and.returnValue(staleDownload.asObservable());

    component.restoreFilesAndSettings(
      {
        id: 'session-old',
        materialCode: 'PLA',
        quality: 'standard',
        nozzleDiameterMm: 0.4,
        layerHeightMm: 0.2,
        infillPercent: 15,
        infillPattern: 'grid',
        supportsEnabled: true,
      },
      [
        {
          id: 'line-old',
          originalFilename: 'old-part.stl',
          quantity: 1,
          materialCode: 'PLA',
          quality: 'standard',
          nozzleDiameterMm: 0.4,
          layerHeightMm: 0.2,
          infillPercent: 15,
          infillPattern: 'grid',
          supportsEnabled: true,
        },
      ],
    );

    component.result.set(createResult('session-new'));
    staleDownload.next(new Blob(['old']));
    staleDownload.complete();

    expect(uploadForm.setFiles).not.toHaveBeenCalled();
    expect(component.loading()).toBeTrue();
  });

  it('ignores in-flight restores after the user changes print settings', () => {
    const { component, estimator, uploadForm } = createComponent();
    const staleDownload = new Subject<Blob>();
    const baselineSettings = {
      mode: 'advanced',
      material: 'pla',
      quality: 'standard',
      nozzleDiameter: 0.4,
      layerHeight: 0.2,
      infillDensity: 15,
      infillPattern: 'grid',
      supportEnabled: true,
    } as const;
    component.mode.set('advanced');
    component.result.set(createResult('session-1'));
    component.loading.set(true);
    component['baselinePrintSettings'] = baselineSettings;
    component['baselineItemStates'] = [
      { fileName: 'part-a.stl', settings: baselineSettings },
    ];

    estimator.getLineItemContent.and.returnValue(staleDownload.asObservable());
    uploadForm.getCurrentRequestDraft.and.returnValue({
      ...createDraftRequest(),
      mode: 'advanced',
      layerHeight: 0.1,
      items: createDraftRequest().items.map((item) => ({
        ...item,
        layerHeight: 0.1,
      })),
    });

    component.restoreFilesAndSettings(
      {
        id: 'session-1',
        materialCode: 'PLA',
        quality: 'standard',
        nozzleDiameterMm: 0.4,
        layerHeightMm: 0.2,
        infillPercent: 15,
        infillPattern: 'grid',
        supportsEnabled: true,
      },
      [
        {
          id: 'line-1',
          originalFilename: 'part-a.stl',
          quantity: 1,
          materialCode: 'PLA',
          quality: 'standard',
          nozzleDiameterMm: 0.4,
          layerHeightMm: 0.2,
          infillPercent: 15,
          infillPattern: 'grid',
          supportsEnabled: true,
        },
      ],
    );

    component.onUploadPrintSettingsChange({
      ...baselineSettings,
      material: 'PLA',
      layerHeight: 0.1,
    });
    staleDownload.next(new Blob(['old']));
    staleDownload.complete();

    expect(component.requiresRecalculation()).toBeTrue();
    expect(uploadForm.setFiles).not.toHaveBeenCalled();
    expect(component.loading()).toBeFalse();
  });
});
