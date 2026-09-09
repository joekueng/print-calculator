import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { firstValueFrom, of } from 'rxjs';
import enTranslations from '../../../assets/i18n/en.json';
import itTranslations from '../../../assets/i18n/it.json';
import { MaterialsPageComponent } from './materials-page.component';
import { MaterialId } from './materials-page.types';
import { PublicMediaService } from '../../core/services/public-media.service';
import { LanguageService } from '../../core/services/language.service';

describe('MaterialsPageComponent', () => {
  let fixture: ComponentFixture<MaterialsPageComponent>;
  let component: MaterialsPageComponent;
  let translate: TranslateService;
  let publicMediaService: jasmine.SpyObj<PublicMediaService>;

  const currentLang = signal<'it' | 'en' | 'de' | 'fr'>('it');
  const languageServiceStub = {
    currentLang,
    localizedPath: (path: string) => `/${currentLang()}${path}`,
  };

  async function switchLanguage(lang: 'it' | 'en') {
    await firstValueFrom(translate.use(lang));
    currentLang.set(lang);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    currentLang.set('it');

    publicMediaService = jasmine.createSpyObj<PublicMediaService>(
      'PublicMediaService',
      ['getUsageCollections', 'pickPrimaryUsage', 'toDisplayImage'],
    );
    publicMediaService.getUsageCollections.and.returnValue(of({}));

    await TestBed.configureTestingModule({
      imports: [MaterialsPageComponent, TranslateModule.forRoot()],
      providers: [
        { provide: PublicMediaService, useValue: publicMediaService },
        { provide: LanguageService, useValue: languageServiceStub },
      ],
    }).compileComponents();

    translate = TestBed.inject(TranslateService);
    translate.setFallbackLang('it');
    translate.setTranslation('it', itTranslations);
    translate.setTranslation('en', enTranslations);
    await firstValueFrom(translate.use('it'));

    fixture = TestBed.createComponent(MaterialsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders the selector below the radar without a duplicate legend', () => {
    const host = fixture.nativeElement as HTMLElement;
    const heroTitle = host.querySelector('.ui-simple-hero__title');
    const chartCard = host.querySelector(
      '.materials-panel--chart',
    ) as HTMLElement;
    const selectorPanel = chartCard.querySelector(
      '.selector-panel',
    ) as HTMLElement;

    expect(heroTitle?.textContent?.trim()).toBe('Qualita e Materiali');
    expect(chartCard).toBeTruthy();
    expect(selectorPanel).toBeTruthy();
    expect(chartCard.querySelector('.chart-legend')).toBeNull();
    expect(host.querySelectorAll('.material-card').length).toBe(0);
  });

  it('updates radar and table content when a material is toggled', () => {
    component.toggleMaterial('pla-matte');
    fixture.detectChanges();

    expect(
      component.selectedMaterials().map((material) => material.id),
    ).toEqual(['pla-basic', 'asa', 'pet-cf', 'pla-matte']);

    const tableHeaders = Array.from(
      fixture.nativeElement.querySelectorAll(
        'thead th',
      ) as NodeListOf<HTMLTableCellElement>,
    ).map((cell) => cell.textContent?.trim());

    expect(tableHeaders).toContain('PLA Matte');
    expect(
      fixture.nativeElement.querySelectorAll('.selector-chip.is-selected')
        .length,
    ).toBe(4);
  });

  it('keeps logarithmic flexibility fixed when TPU is added and removed, preserving raw values', () => {
    component.selectedMaterialIds.set(['pc', 'pla-basic', 'petg-extrudr']);
    const flexibility = () => component.radarSeries().map((series) =>
      series.values.find((point) => point.axis.id === 'elongation')!,
    );
    const initial = flexibility();
    expect(initial.map((point) => point.rawValue)).toEqual([3.8, 12, 18]);
    expect(initial[0].score).toBe(0);
    expect(initial[1].score).toBeGreaterThan(15);
    expect(initial[1].score).toBeLessThan(25);
    expect(initial[2].score).toBeGreaterThan(initial[1].score);

    component.toggleMaterial('tpu-95a-hf');
    expect(flexibility().slice(0, 3)).toEqual(initial);
    expect(flexibility()[3].score).toBe(100);
    expect(flexibility()[1].score - flexibility()[0].score).toBeGreaterThan(15);

    component.toggleMaterial('tpu-95a-hf');
    expect(flexibility().map((point) => point.score)).toEqual(initial.map((point) => point.score));
    component.selectedMaterialIds.set(['petg-extrudr']);
    expect(flexibility()[0]).toEqual(initial[2]);
    expect(Number.isFinite(flexibility()[0].x)).toBeTrue();
  });

  it('ranks printability according to our printer in both the radar and table', () => {
    const expected: MaterialId[] = ['pla-basic', 'pla-matte', 'pla-tough-plus', 'petg-extrudr',
      'tpu-95a-hf', 'pc', 'pet-cf', 'pa12-cf', 'asa'];
    const ranked = [...component.materials()].sort((a, b) =>
      b.metrics.printability - a.metrics.printability,
    );
    expect(ranked.map((material) => material.id)).toEqual(expected);
    const scores = expected.map((id) => {
      component.selectedMaterialIds.set([id]);
      const point = component.radarSeries()[0].values.find((value) => value.axis.id === 'printability')!;
      expect(component.comparisonRows().find((row) => row.id === 'printability')?.values)
        .toEqual([point.rawValue.toFixed(0)]);
      return point.score;
    });
    scores.slice(1).forEach((score, index) => expect(score).toBeLessThan(scores[index]));
  });

  it('shows PETG and the food-contact section with its manufacturer source', async () => {
    await switchLanguage('en');
    component.toggleMaterial('petg-extrudr');
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(component.materialById().get('petg-extrudr')?.name).toBe('PETG');
    expect(host.querySelector('thead')?.textContent).toContain('PETG');
    const section = host.querySelector('[aria-labelledby="petg-food-contact-title"]');
    expect(section?.textContent).toContain('We also offer a PETG variant');
    expect(section?.textContent).toContain('does not automatically certify');
    expect(section?.querySelector('a')?.href).toBe('https://s3.extrudr.com/extrudr-media/datasheets/ris/ris-en/petg-RIS-en.pdf');
  });

  it('keeps selected chip and radar colors aligned by selection order', () => {
    component.toggleMaterial('pla-matte');
    component.toggleMaterial('tpu-95a-hf');
    fixture.detectChanges();

    expect(component.legendDotColor('pla-basic')).toBe('#c23b22');
    expect(component.legendDotColor('asa')).toBe('#2663d3');
    expect(component.legendDotColor('pet-cf')).toBe('#0f8f6f');
    expect(component.legendDotColor('pla-matte')).toBe('#8a44c9');
    expect(component.radarSeries().map((series) => series.color)).toEqual([
      '#c23b22',
      '#2663d3',
      '#0f8f6f',
      '#8a44c9',
      '#c77510',
    ]);
  });

  it('updates static copy, computed content and localized links when the language changes', async () => {
    const host = fixture.nativeElement as HTMLElement;
    const heroLink = host.querySelector(
      '.materials-inline-link',
    ) as HTMLAnchorElement;
    const calculatorSection = host.querySelector(
      '.materials-section--muted',
    ) as HTMLElement;

    expect(
      host.querySelector('.ui-simple-hero__title')?.textContent?.trim(),
    ).toBe('Qualita e Materiali');
    expect(calculatorSection.textContent).toContain(
      'Come usare il calcolatore',
    );
    expect(calculatorSection.textContent).toContain(
      'Parametri del calcolatore',
    );
    expect(calculatorSection.textContent).toContain('prezzo finale di stampa');
    expect(component.radarAxes()[1]?.label).toBe('Stampabilita');
    expect(component.comparisonRows()[0]?.label).toBe(
      'Stampabilita [indice 0-100]',
    );
    expect(heroLink.getAttribute('href')).toBe('#materials-calculator');
    expect(calculatorSection.getAttribute('id')).toBe('materials-calculator');
    expect(
      Array.from(
        calculatorSection.querySelectorAll('.calculator-fact-actions a'),
      ).map((link) => link.getAttribute('href')),
    ).toEqual([
      '/it/calculator',
      '/it/calculator/basic#calculator-workspace',
      '/it/calculator/advanced#calculator-workspace',
    ]);

    await switchLanguage('en');

    expect(
      host.querySelector('.ui-simple-hero__title')?.textContent?.trim(),
    ).toBe('Quality & Materials');
    expect(calculatorSection.textContent).toContain(
      'How to use the calculator',
    );
    expect(calculatorSection.textContent).toContain('Calculator parameters');
    expect(calculatorSection.textContent).toContain('final print price');
    expect(component.radarAxes()[1]?.label).toBe('Printability');
    expect(component.comparisonRows()[0]?.label).toBe(
      'Printability [0-100 index]',
    );
    expect(
      Array.from(
        calculatorSection.querySelectorAll('.calculator-fact-actions a'),
      ).map((link) => link.getAttribute('href')),
    ).toEqual([
      '/en/calculator',
      '/en/calculator/basic#calculator-workspace',
      '/en/calculator/advanced#calculator-workspace',
    ]);
  });
});
