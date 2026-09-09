import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { firstValueFrom, of } from 'rxjs';
import deTranslations from '../../../../../assets/i18n/de.json';
import itTranslations from '../../../../../assets/i18n/it.json';
import { LanguageService } from '../../../../core/services/language.service';
import {
  OptionsResponse,
  QuoteEstimatorService,
} from '../../services/quote-estimator.service';
import { UploadFormComponent } from './upload-form.component';

describe('UploadFormComponent', () => {
  let fixture: ComponentFixture<UploadFormComponent>;
  let component: UploadFormComponent;
  let translate: TranslateService;

  const currentLang = signal<'it' | 'en' | 'de' | 'fr'>('de');
  const languageServiceStub = {
    currentLang,
    selectedLang: () => currentLang(),
  };
  const options: OptionsResponse = {
    materials: [
      {
        code: 'TPU',
        label: 'TPU (Flexible)',
        isTechnical: false,
        variants: [],
      },
    ],
    qualities: [
      { id: 'draft', label: 'Draft' },
      { id: 'standard', label: 'Standard' },
      { id: 'extra_fine', label: 'High Definition' },
    ],
    infillPatterns: [
      { id: 'grid', label: 'Grid' },
      { id: 'gyroid', label: 'Gyroid' },
    ],
    nozzleDiameters: [{ value: 0.4, label: '0.4 mm (Standard)' }],
    layerHeights: [{ value: 0.2, label: '0.20 mm' }],
    layerHeightsByNozzle: [
      {
        nozzleDiameter: 0.4,
        layerHeights: [{ value: 0.2, label: '0.20 mm' }],
      },
    ],
  };

  beforeEach(async () => {
    currentLang.set('de');

    await TestBed.configureTestingModule({
      imports: [UploadFormComponent, TranslateModule.forRoot()],
      providers: [
        {
          provide: QuoteEstimatorService,
          useValue: { getOptions: () => of(options) },
        },
        { provide: LanguageService, useValue: languageServiceStub },
      ],
    }).compileComponents();

    translate = TestBed.inject(TranslateService);
    translate.setFallbackLang('it');
    translate.setTranslation('de', deTranslations);
    translate.setTranslation('it', itTranslations);
    await firstValueFrom(translate.use('de'));

    fixture = TestBed.createComponent(UploadFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('refreshes option labels without changing selections when language changes', async () => {
    component.form.get('quality')?.setValue('extra_fine');

    expect(component.materials()[0]?.label).toBe('TPU (Flexibel)');
    expect(component.qualities().map((quality) => quality.label)).toEqual([
      'Entwurf',
      'Standard',
      'Hohe Auflösung',
    ]);
    expect(component.infillPatterns()[0]?.label).toBe('Gitter');

    await firstValueFrom(translate.use('it'));
    currentLang.set('it');
    fixture.detectChanges();

    expect(component.materials()[0]?.label).toBe('TPU (Flessibile)');
    expect(component.qualities().map((quality) => quality.label)).toEqual([
      'Bozza',
      'Standard',
      'Alta definizione',
    ]);
    expect(component.infillPatterns()[0]?.label).toBe('Griglia');
    expect(component.form.get('quality')?.value).toBe('extra_fine');
  });
});
