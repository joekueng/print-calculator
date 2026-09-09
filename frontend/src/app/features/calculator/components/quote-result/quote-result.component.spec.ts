import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { QuoteResultComponent } from './quote-result.component';
import { QuoteResult } from '../../services/quote-estimator.service';

describe('QuoteResultComponent', () => {
  let fixture: ComponentFixture<QuoteResultComponent>;
  let component: QuoteResultComponent;

  const createResult = (): QuoteResult => ({
    sessionId: 'session-1',
    items: [
      {
        id: 'line-1',
        fileName: 'part-a.stl',
        unitPrice: 2,
        unitTime: 120,
        unitWeight: 1.2,
        quantity: 2,
      },
      {
        id: 'line-2',
        fileName: 'part-b.stl',
        unitPrice: 1.5,
        unitTime: 60,
        unitWeight: 0.5,
        quantity: 1,
      },
    ],
    setupCost: 5,
    globalMachineCost: 0,
    currency: 'CHF',
    totalPrice: 0,
    totalTimeHours: 0,
    totalTimeMinutes: 0,
    totalWeight: 0,
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        QuoteResultComponent,
        TranslateModule.forRoot(),
        HttpClientTestingModule,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(QuoteResultComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('result', createResult());
    fixture.detectChanges();
  });

  it('emits quantity changes with clamped max quantity', () => {
    spyOn(component.itemChange, 'emit');

    component.updateQuantity(0, 999);
    component.flushQuantityUpdate(0);

    expect(component.items()[0].quantity).toBe(component.maxInputQuantity);
    expect(component.itemChange.emit).toHaveBeenCalledWith({
      id: 'line-1',
      index: 0,
      fileName: 'part-a.stl',
      quantity: component.maxInputQuantity,
    });
  });

  it('computes totals from local item quantities', () => {
    component.updateQuantity(1, 3);

    const totals = component.totals();
    expect(totals.price).toBe(13.5);
    expect(totals.hours).toBe(0);
    expect(totals.minutes).toBe(7);
    expect(totals.weight).toBe(4);
  });

  it('flags over-limit quantities for direct order', () => {
    component.updateQuantity(0, 101);
    expect(component.hasQuantityOverLimit()).toBeTrue();
  });

  it('shows every changed print setting as a compact detail', () => {
    const result = createResult();
    result.items[0] = {
      ...result.items[0],
      material: 'PETG',
      nozzleDiameter: 0.6,
      layerHeight: 0.3,
      infillDensity: 30,
      infillPattern: 'gyroid',
      supportEnabled: false,
    };
    fixture.componentRef.setInput('result', result);
    fixture.componentRef.setInput('itemSettingsDiffByFileName', {
      'part-a.stl': {
        differences: [
          'PETG',
          'nozzle:0.6',
          'layer:0.3',
          'infill:30%',
          'pattern:gyroid',
          'support:off',
        ],
      },
    });
    fixture.detectChanges();

    const details = component.getItemSettingDetails(result.items[0]);

    expect(details).toEqual([
      { key: 'material', labelKey: 'CALC.MATERIAL', value: 'PETG' },
      { key: 'nozzle', labelKey: 'CALC.NOZZLE', value: '0.6 mm' },
      { key: 'layer', labelKey: 'CALC.LAYER_HEIGHT', value: '0.3 mm' },
      { key: 'infill', labelKey: 'CALC.INFILL', value: '30%' },
      { key: 'pattern', labelKey: 'CALC.PATTERN', value: 'Gyroid' },
      { key: 'support', labelKey: 'CALC.SUPPORT', value: 'OFF' },
    ]);
    expect(
      fixture.nativeElement.querySelectorAll('.item-setting-detail').length,
    ).toBe(6);
  });

  it('does not show print-setting details for standard files', () => {
    expect(component.getItemSettingDetails(createResult().items[0])).toEqual(
      [],
    );
    expect(
      fixture.nativeElement.querySelector('.item-settings-details'),
    ).toBeNull();
  });

  it('shows changed global settings when all files use the same settings', () => {
    const result = createResult();
    result.items[0] = {
      ...result.items[0],
      material: 'TPU',
      quality: 'standard',
      nozzleDiameter: 0.6,
      layerHeight: 0.3,
      infillDensity: 15,
      infillPattern: 'gyroid',
      supportEnabled: true,
    };
    fixture.componentRef.setInput('result', result);
    fixture.componentRef.setInput('itemSettingsDiffByFileName', {});
    fixture.detectChanges();

    expect(component.getItemSettingDetails(result.items[0])).toEqual([
      { key: 'material', labelKey: 'CALC.MATERIAL', value: 'TPU' },
      { key: 'nozzle', labelKey: 'CALC.NOZZLE', value: '0.6 mm' },
      { key: 'layer', labelKey: 'CALC.LAYER_HEIGHT', value: '0.3 mm' },
      { key: 'pattern', labelKey: 'CALC.PATTERN', value: 'Gyroid' },
    ]);
    expect(
      fixture.nativeElement.querySelectorAll('.item-setting-detail').length,
    ).toBe(4);
  });
});
