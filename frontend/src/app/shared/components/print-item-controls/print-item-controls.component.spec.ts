import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { LanguageService } from '../../../core/services/language.service';
import { PrintItemControlsComponent } from './print-item-controls.component';
import itTranslations from '../../../../assets/i18n/it.json';

describe('PrintItemControlsComponent', () => {
  it('renders translated controls, commits quantity on blur, and selects material variants', async () => {
    await TestBed.configureTestingModule({
      imports: [PrintItemControlsComponent, TranslateModule.forRoot()],
      providers: [
        { provide: LanguageService, useValue: { selectedLang: () => 'it' } },
      ],
    }).compileComponents();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('it', itTranslations);
    translate.use('it');
    const fixture = TestBed.createComponent(PrintItemControlsComponent);
    fixture.componentRef.setInput('quantity', 1);
    fixture.componentRef.setInput('color', 'Nero');
    fixture.componentRef.setInput('commitOnBlur', true);
    fixture.componentRef.setInput('variants', [
      {
        id: 12,
        name: 'Nero',
        colorName: 'Nero',
        colorLabelIt: 'Nero',
        hexColor: '#000000',
        finishType: 'GLOSSY',
        stockSpools: 1,
        stockFilamentGrams: 1000,
        isOutOfStock: false,
      },
    ]);
    const quantity = jasmine.createSpy('quantity');
    const color = jasmine.createSpy('color');
    fixture.componentInstance.quantityChange.subscribe(quantity);
    fixture.componentInstance.colorChange.subscribe(color);
    fixture.detectChanges();
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).not.toContain('CALC.');
    expect(translate.instant('CHECKOUT.ERR_UPDATE_ITEM')).not.toBe(
      'CHECKOUT.ERR_UPDATE_ITEM',
    );
    const input = element.querySelector('input')!;
    input.value = '3';
    input.dispatchEvent(new Event('input'));
    expect(quantity).not.toHaveBeenCalled();
    input.dispatchEvent(new Event('blur'));
    expect(quantity).toHaveBeenCalledWith(3);
    element.querySelector<HTMLButtonElement>('.trigger')!.click();
    fixture.detectChanges();
    element.querySelector<HTMLElement>('.color-item')!.click();
    expect(color).toHaveBeenCalledWith({
      colorName: 'Nero',
      filamentVariantId: 12,
    });
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(input.disabled).toBeTrue();
    expect(
      element.querySelector<HTMLButtonElement>('.trigger')!.disabled,
    ).toBeTrue();
  });
});
