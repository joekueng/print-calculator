import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { FormBuilder } from '@angular/forms';
import { PLATFORM_ID } from '@angular/core';
import { CheckoutComponent } from './checkout.component';
import { QuoteEstimatorService } from '../calculator/services/quote-estimator.service';
import { LanguageService } from '../../core/services/language.service';

describe('CheckoutComponent', () => {
  it('requires a shipping quote before order submission', () => {
    const { component } = createComponent();
    component.quoteSession.set({ shippingQuote: { status: 'MANUAL_QUOTE' } });
    expect(component.shippingUnavailable()).toBeTrue();
    component.onSubmit();
    expect(component.isSubmitting()).toBeFalse();
    component.quoteSession.set({ shippingQuote: { status: 'QUOTED' } });
    expect(component.shippingUnavailable()).toBeFalse();
    component.quoteSession.set({ session: { sessionType: 'SHOP_CART' } });
    expect(component.shippingUnavailable()).toBeFalse();
  });
  it('waits for backend totals after changing CAD quantity or color', () => {
    const { component, quoteService } = createComponent();
    component.sessionId = 'cad-session';
    const response = new Subject<unknown>();
    quoteService.updateCadCheckoutItem.and.returnValue(response);
    const item = { id: 'item', quantity: 1, filamentVariantId: 1 };
    component.updateCadItem(item, 3, 2);
    expect(component.updatingItem()).toBeTrue();
    expect(quoteService.updateCadCheckoutItem).toHaveBeenCalledWith(
      'cad-session',
      'item',
      { quantity: 3, filamentVariantId: 2 },
    );
    const session = {
      grandTotalChf: 42,
      items: [{ ...item, quantity: 3, filamentVariantId: 2 }],
    };
    response.next(session);
    expect(component.quoteSession()).toBe(session);
    expect(component.updatingItem()).toBeFalse();
  });

  it('blocks checkout on invalid quantities and failed CAD changes', () => {
    const { component, quoteService } = createComponent();
    component.sessionId = 'cad-session';
    const item = { id: 'item', quantity: 1, filamentVariantId: 1 };
    component.updateCadItem(item, 1.5, 1);
    expect(quoteService.updateCadCheckoutItem).not.toHaveBeenCalled();
    expect(component.itemEditError()).toBeTrue();
    quoteService.updateCadCheckoutItem.and.returnValue(
      throwError(() => new Error('failed')),
    );
    component.updateCadItem(item, 3, 1);
    expect(component.itemEditError()).toBeTrue();
    expect(component.updatingItem()).toBeFalse();
  });

  function createComponent(
    platformId: Object = 'browser',
    queryParams: Record<string, unknown> = {},
  ) {
    TestBed.resetTestingModule();

    const quoteService = jasmine.createSpyObj<QuoteEstimatorService>(
      'QuoteEstimatorService',
      [
        'getQuoteSession',
        'getOptions',
        'getLineItemStlPreview',
        'updateCadCheckoutItem',
      ],
    );

    quoteService.getOptions.and.returnValue(of({ materials: [] } as any));
    quoteService.getQuoteSession.and.returnValue(
      of({
        session: { id: 'session-1', status: 'ACTIVE' },
        items: [],
      }),
    );

    TestBed.configureTestingModule({
      providers: [
        FormBuilder,
        {
          provide: QuoteEstimatorService,
          useValue: quoteService,
        },
        {
          provide: Router,
          useValue: jasmine.createSpyObj<Router>('Router', ['navigate']),
        },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: of(queryParams),
          },
        },
        {
          provide: LanguageService,
          useValue: {
            selectedLang: () => 'it',
          },
        },
        {
          provide: PLATFORM_ID,
          useValue: platformId,
        },
      ],
    });

    const component = TestBed.runInInjectionContext(
      () => new CheckoutComponent(TestBed.inject(PLATFORM_ID)),
    );
    const router = TestBed.inject(Router) as jasmine.SpyObj<Router>;

    return {
      component,
      quoteService,
      router,
    };
  }

  it('prefers shop variant metadata for labels and swatches', () => {
    const { component } = createComponent();
    const item = {
      lineItemType: 'SHOP_PRODUCT',
      displayName: 'Desk Cable Clip',
      shopProductName: 'Desk Cable Clip',
      shopVariantLabel: 'Coral Red',
      shopVariantColorName: 'Coral Red',
      shopVariantColorHex: '#ff6b6b',
      colorCode: 'Rosso',
    };

    expect(component.isShopItem(item)).toBeTrue();
    expect(component.itemDisplayName(item)).toBe('Desk Cable Clip');
    expect(component.itemVariantLabel(item)).toBe('Coral Red');
    expect(component.itemColorLabel(item)).toBe('Coral Red');
    expect(component.itemColorSwatch(item)).toBe('#ff6b6b');
    expect(component.showItemMaterial(item)).toBeFalse();
    expect(component.showItemPrintMetrics(item)).toBeFalse();
  });

  it('skips session and palette fetch during SSR', () => {
    const { component, quoteService } = createComponent('server', {
      session: 'session-1',
    });

    component.ngOnInit();

    expect(quoteService.getOptions).not.toHaveBeenCalled();
    expect(quoteService.getQuoteSession).not.toHaveBeenCalled();
    expect(component.loading).toBeTrue();
  });

  it('loads session data in the browser from the query param', () => {
    const { component, quoteService } = createComponent('browser', {
      session: 'session-1',
    });

    component.ngOnInit();

    expect(quoteService.getOptions).toHaveBeenCalled();
    expect(quoteService.getQuoteSession).toHaveBeenCalledWith('session-1');
    expect(component.quoteSession()?.session?.id).toBe('session-1');
    expect(component.error).toBeNull();
    expect(component.loading).toBeFalse();
  });
});
