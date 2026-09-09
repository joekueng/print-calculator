import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import {
  PublicMediaDisplayImage,
  PublicMediaService,
  PublicMediaUsageCollectionMap,
  buildPublicMediaUsageScopeKey,
} from '../../core/services/public-media.service';
import { LanguageService } from '../../core/services/language.service';
import {
  AxisGuide,
  CalculatorFact,
  CalculatorFactConfig,
  CalculatorParameter,
  CalculatorParameterConfig,
  ComparisonRow,
  ComparisonRowConfig,
  MaterialConfig,
  MaterialId,
  MaterialRecord,
  MaterialSeriesStyle,
  MaterialSource,
  MaterialsPageTranslations,
  QualityVisualCard,
  QualityVisualGuideConfig,
  RadarAxis,
  RadarAxisConfig,
  RadarPoint,
  RadarSeries,
} from './materials-page.types';

const MATERIAL_CONFIGS: readonly MaterialConfig[] = [
  {
    id: 'tpu-95a-hf',
    metrics: {
      priceChfKg: 30,
      densityGcm3: 1.22,
      tensileMpa: 27,
      modulusGpa: 0.01,
      elongationPct: 650,
      hdtC: 0,
      extrusionC: '220 - 240',
      printability: 70,
      layerRangeMm: '0.20 - 0.32',
    },
    sources: [
      {
        id: 'wikipedia-tpu',
        kindId: 'wikipedia',
        url: 'https://en.wikipedia.org/wiki/Thermoplastic_polyurethane',
      },
      {
        id: 'bambu-tpu-95a-hf',
        kindId: 'product-sheet',
        url: 'https://eu.store.bambulab.com/it/products/tpu-95a-hf',
      },
      {
        id: 'ultimaker-tpu-95a',
        kindId: 'tech-sheet',
        url: 'https://ultimaker.com/materials/s-series-tpu-95a/',
      },
    ],
  },
  {
    id: 'pla-basic',
    metrics: {
      priceChfKg: 18,
      densityGcm3: 1.24,
      tensileMpa: 35,
      modulusGpa: 2.58,
      elongationPct: 12,
      hdtC: 57,
      extrusionC: '190 - 230',
      printability: 96,
      layerRangeMm: '0.12 - 0.24',
    },
    sources: [
      {
        id: 'wikipedia-pla',
        kindId: 'wikipedia',
        url: 'https://en.wikipedia.org/wiki/Polylactic_acid',
      },
      {
        id: 'bambu-pla-basic',
        kindId: 'product-sheet',
        url: 'https://eu.store.bambulab.com/it/products/pla-basic-filament',
      },
      {
        id: 'ultimaker-pla',
        kindId: 'tech-sheet',
        url: 'https://ultimaker.com/materials/pla/',
      },
    ],
  },
  {
    id: 'pla-matte',
    metrics: {
      priceChfKg: 18,
      densityGcm3: 1.31,
      tensileMpa: 30,
      modulusGpa: 1.96,
      elongationPct: 15,
      hdtC: 58,
      extrusionC: '190 - 230',
      printability: 94,
      layerRangeMm: '0.16 - 0.24',
    },
    sources: [
      {
        id: 'wikipedia-pla',
        kindId: 'wikipedia',
        url: 'https://en.wikipedia.org/wiki/Polylactic_acid',
      },
      {
        id: 'bambu-pla-matte',
        kindId: 'product-sheet',
        url: 'https://eu.store.bambulab.com/it/products/pla-matte',
      },
    ],
  },
  {
    id: 'pla-tough-plus',
    metrics: {
      priceChfKg: 22,
      densityGcm3: 1.21,
      tensileMpa: 35,
      modulusGpa: 1.86,
      elongationPct: 9,
      hdtC: 61,
      extrusionC: '220 - 250',
      printability: 89,
      layerRangeMm: '0.16 - 0.24',
    },
    sources: [
      {
        id: 'wikipedia-pla',
        kindId: 'wikipedia',
        url: 'https://en.wikipedia.org/wiki/Polylactic_acid',
      },
      {
        id: 'bambu-pla-tough',
        kindId: 'product-sheet',
        url: 'https://eu.store.bambulab.com/it/products/pla-tough-upgrade',
      },
    ],
  },
  {
    id: 'asa',
    metrics: {
      priceChfKg: 23,
      densityGcm3: 1.07,
      tensileMpa: 45,
      modulusGpa: 2.1,
      elongationPct: 10,
      hdtC: 95,
      extrusionC: '240 - 260',
      printability: 64,
      layerRangeMm: '0.20 - 0.28',
    },
    sources: [
      {
        id: 'wikipedia-asa',
        kindId: 'wikipedia',
        url: 'https://en.wikipedia.org/wiki/Acrylonitrile_styrene_acrylate',
      },
      {
        id: 'bambu-asa',
        kindId: 'product-sheet',
        url: 'https://eu.store.bambulab.com/it/products/asa-filament',
      },
      {
        id: 'ultimaker-asa',
        kindId: 'tech-sheet',
        url: 'https://ultimaker.com/materials/method-series-asa/',
      },
    ],
  },
  {
    id: 'pc',
    metrics: {
      priceChfKg: 39,
      densityGcm3: 1.2,
      tensileMpa: 55,
      modulusGpa: 2.11,
      elongationPct: 3.8,
      hdtC: 112,
      extrusionC: '260 - 280',
      printability: 47,
      layerRangeMm: '0.20 - 0.28',
    },
    sources: [
      {
        id: 'wikipedia-pc',
        kindId: 'wikipedia',
        url: 'https://en.wikipedia.org/wiki/Polycarbonate',
      },
      {
        id: 'bambu-pc',
        kindId: 'product-sheet',
        url: 'https://eu.store.bambulab.com/it/products/pc-filament',
      },
      {
        id: 'ultimaker-pc',
        kindId: 'tech-sheet',
        url: 'https://ultimaker.com/materials/s-series-pc/',
      },
    ],
  },
  {
    id: 'pa12-cf',
    metrics: {
      priceChfKg: 50,
      densityGcm3: 1.06,
      tensileMpa: 60,
      modulusGpa: 3.3,
      elongationPct: 16,
      hdtC: 185,
      extrusionC: '260 - 290',
      printability: 42,
      layerRangeMm: '0.20 - 0.28',
    },
    sources: [
      {
        id: 'wikipedia-nylon-12',
        kindId: 'wikipedia',
        url: 'https://en.wikipedia.org/wiki/Nylon_12',
      },
      {
        id: 'ultimaker-pa12-cf',
        kindId: 'tech-sheet',
        url: 'https://ultimaker.com/materials/method-series-nylon-12-carbon-fiber/',
      },
      {
        id: 'product-pa12-cf',
        kindId: 'product-sheet',
        url: 'https://www.amazon.de/-/en/ERYONE-Carbon-Filament-Printer-Printers/dp/B0CHDS7YD2/',
      },
    ],
  },
  {
    id: 'petg-extrudr',
    metrics: {
      priceChfKg: 35,
      densityGcm3: 1.27,
      tensileMpa: 50,
      modulusGpa: 2.1,
      elongationPct: 18,
      hdtC: 80,
      extrusionC: '210 - 230',
      printability: 88,
      layerRangeMm: '0.12 - 0.28',
    },
    sources: [
      {
        id: 'extrudr-petg-white',
        kindId: 'product-sheet',
        url: 'https://www.3djake.ch/en-CH/extrudr/mf-petg-white?sai=768',
      },
    ],
  },
  {
    id: 'pet-cf',
    metrics: {
      priceChfKg: 83,
      densityGcm3: 1.29,
      tensileMpa: 74,
      modulusGpa: 4.73,
      elongationPct: 4,
      hdtC: 205,
      extrusionC: '260 - 290',
      printability: 70,
      layerRangeMm: '0.20 - 0.28',
    },
    sources: [
      {
        id: 'wikipedia-pet',
        kindId: 'wikipedia',
        url: 'https://en.wikipedia.org/wiki/Polyethylene_terephthalate',
      },
      {
        id: 'wikipedia-cfrp',
        kindId: 'wikipedia',
        url: 'https://en.wikipedia.org/wiki/Carbon_fiber_reinforced_polymer',
      },
      {
        id: 'bambu-pet-cf',
        kindId: 'product-sheet',
        url: 'https://eu.store.bambulab.com/it/products/pet-cf',
      },
    ],
  },
];

const RADAR_AXIS_CONFIGS: readonly RadarAxisConfig[] = [
  {
    id: 'economy',
    lowerIsBetter: true,
    accessor: (material) => material.metrics.priceChfKg,
  },
  {
    id: 'printability',
    accessor: (material) => material.metrics.printability,
  },
  {
    id: 'tensile',
    accessor: (material) => material.metrics.tensileMpa,
  },
  {
    id: 'modulus',
    accessor: (material) => material.metrics.modulusGpa,
  },
  {
    id: 'elongation',
    accessor: (material) => material.metrics.elongationPct,
  },
  {
    id: 'hdt',
    accessor: (material) => material.metrics.hdtC,
  },
];

const COMPARISON_ROW_CONFIGS: readonly ComparisonRowConfig[] = [
  {
    id: 'printability',
    accessor: (material) => material.metrics.printability,
    fractionDigits: 0,
  },
  {
    id: 'layer-range',
    accessor: (material) => material.metrics.layerRangeMm,
  },
  {
    id: 'price',
    accessor: (material) => material.metrics.priceChfKg,
    fractionDigits: 0,
  },
  {
    id: 'density',
    accessor: (material) => material.metrics.densityGcm3,
    fractionDigits: 2,
  },
  {
    id: 'tensile',
    accessor: (material) => material.metrics.tensileMpa,
    fractionDigits: 0,
  },
  {
    id: 'modulus',
    accessor: (material) => material.metrics.modulusGpa,
    fractionDigits: 2,
  },
  {
    id: 'elongation',
    accessor: (material) => material.metrics.elongationPct,
    fractionDigits: 1,
  },
  {
    id: 'hdt',
    accessor: (material) => material.metrics.hdtC,
    fractionDigits: 0,
  },
  {
    id: 'extrusion',
    accessor: (material) => material.metrics.extrusionC,
  },
];

const CALCULATOR_FACT_CONFIGS: readonly CalculatorFactConfig[] = [
  {
    id: 'overview',
    path: '/calculator',
  },
  {
    id: 'basic',
    path: '/calculator/basic#calculator-workspace',
  },
  {
    id: 'advanced',
    path: '/calculator/advanced#calculator-workspace',
  },
];

const CALCULATOR_PARAMETER_CONFIGS: readonly CalculatorParameterConfig[] = [
  { id: 'material' },
  { id: 'quality' },
  { id: 'nozzleDiameter' },
  { id: 'layerHeight' },
  { id: 'infill' },
  { id: 'infillPattern' },
  { id: 'supports' },
];

const QUALITY_VISUAL_GUIDE_CONFIGS: readonly QualityVisualGuideConfig[] = [
  {
    id: 'layer-012',
    categoryId: 'layer',
    usageKey: 'guide-layer-012',
  },
  {
    id: 'layer-020',
    categoryId: 'layer',
    usageKey: 'guide-layer-020',
  },
  {
    id: 'layer-028',
    categoryId: 'layer',
    usageKey: 'guide-layer-028',
  },
  {
    id: 'nozzles-040-060',
    categoryId: 'nozzles',
    usageKey: 'guide-nozzle-060',
  },
  {
    id: 'infill-15',
    categoryId: 'infill',
    usageKey: 'guide-infill-15',
  },
  {
    id: 'infill-40',
    categoryId: 'infill',
    usageKey: 'guide-infill-40',
  },
];

const SERIES_STYLES = [
  { stroke: '#c23b22', fill: 'rgba(194, 59, 34, 0.22)' },
  { stroke: '#2663d3', fill: 'rgba(38, 99, 211, 0.20)' },
  { stroke: '#0f8f6f', fill: 'rgba(15, 143, 111, 0.19)' },
  { stroke: '#8a44c9', fill: 'rgba(138, 68, 201, 0.18)' },
  { stroke: '#c77510', fill: 'rgba(199, 117, 16, 0.17)' },
  { stroke: '#125067', fill: 'rgba(18, 80, 103, 0.16)' },
  { stroke: '#8f2f5f', fill: 'rgba(143, 47, 95, 0.16)' },
  { stroke: '#3b7f1f', fill: 'rgba(59, 127, 31, 0.16)' },
] as const;

const CHART_SIZE = 460;
const CHART_CENTER = CHART_SIZE / 2;
const CHART_RADIUS = 156;
const CHART_LEVELS = 5;
const CHART_INNER_RATIO = 0.09;
const EMPTY_MEDIA_COLLECTIONS: PublicMediaUsageCollectionMap = {};
const MAX_COMPARE_COUNT = 6;

@Component({
  selector: 'app-materials-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './materials-page.component.html',
  styleUrl: './materials-page.component.scss',
})
export class MaterialsPageComponent {
  private readonly publicMediaService = inject(PublicMediaService);
  private readonly translate = inject(TranslateService);
  readonly languageService = inject(LanguageService);

  readonly maxCompareCount = MAX_COMPARE_COUNT;

  readonly selectedMaterialIds = signal<string[]>([
    'pla-basic',
    'asa',
    'pet-cf',
  ]);
  readonly hoveredMaterialId = signal<string | null>(null);

  private readonly pageMediaRequests = QUALITY_VISUAL_GUIDE_CONFIGS.map(
    (guide) => ({
      usageType: 'MATERIALS_PAGE' as const,
      usageKey: guide.usageKey,
    }),
  );

  private readonly mediaByUsage = toSignal(
    this.publicMediaService.getUsageCollections(this.pageMediaRequests),
    { initialValue: EMPTY_MEDIA_COLLECTIONS },
  );

  readonly pageContent = computed<MaterialsPageTranslations>(() => {
    this.languageService.currentLang();
    return this.translate.instant(
      'MATERIALS_PAGE',
    ) as MaterialsPageTranslations;
  });

  readonly materials = computed<readonly MaterialRecord[]>(() => {
    const page = this.pageContent();

    return MATERIAL_CONFIGS.map((config) => {
      const translated = page.MATERIALS[config.id];

      return {
        id: config.id,
        name: translated.NAME,
        summary: translated.SUMMARY,
        qualityTips: translated.QUALITY_TIPS,
        metrics: config.metrics,
        pros: translated.PROS,
        cons: translated.CONS,
        idealFor: translated.IDEAL_FOR,
        sources: config.sources.map((source) => ({
          id: source.id,
          label: translated.SOURCES[source.id].LABEL,
          kind: page.SOURCES.KINDS[source.kindId],
          url: source.url,
        })),
      };
    });
  });

  readonly materialById = computed(
    () => new Map(this.materials().map((material) => [material.id, material])),
  );

  readonly radarAxes = computed<readonly RadarAxis[]>(() => {
    const page = this.pageContent();

    return RADAR_AXIS_CONFIGS.map((config) => {
      const translated = page.RADAR.AXES[config.id];

      return {
        ...config,
        label: translated.LABEL,
        labelLines: translated.LABEL_LINES ?? [translated.LABEL],
        description: translated.DESCRIPTION,
        unit: translated.UNIT,
      };
    });
  });

  readonly calculatorFacts = computed<readonly CalculatorFact[]>(() => {
    const page = this.pageContent();

    return CALCULATOR_FACT_CONFIGS.map((config) => {
      const translated = page.CALCULATOR.FACTS[config.id];

      return {
        id: config.id,
        eyebrow: translated.EYEBROW,
        title: translated.TITLE,
        description: translated.DESCRIPTION,
        detailLabel: translated.DETAIL_LABEL,
        detail: translated.DETAIL,
        noteLabel: translated.NOTE_LABEL,
        note: translated.NOTE,
        ctaLabel: translated.CTA_LABEL,
        path: config.path,
      };
    });
  });

  readonly calculatorParameters = computed<readonly CalculatorParameter[]>(
    () => {
      const page = this.pageContent();

      return CALCULATOR_PARAMETER_CONFIGS.map((config) => {
        const translated = page.CALCULATOR.PARAMETERS.ITEMS[config.id];

        return {
          id: config.id,
          title: translated.TITLE,
          availability: translated.AVAILABILITY,
          explanation: translated.EXPLANATION,
          calculatorEffect: translated.CALCULATOR_EFFECT,
        };
      });
    },
  );

  readonly selectedCount = computed(() => this.selectedMaterialIds().length);

  readonly selectedMaterialStyles = computed<
    ReadonlyMap<string, MaterialSeriesStyle>
  >(() => {
    const styles = new Map<string, MaterialSeriesStyle>();
    this.selectedMaterialIds().forEach((materialId, index) => {
      styles.set(materialId, SERIES_STYLES[index % SERIES_STYLES.length]);
    });
    return styles;
  });

  readonly selectedMaterials = computed(() => {
    const materialById = this.materialById();

    return this.selectedMaterialIds()
      .map((materialId) => materialById.get(materialId as MaterialId))
      .filter((material): material is MaterialRecord => Boolean(material));
  });

  readonly qualityVisualCards = computed<readonly QualityVisualCard[]>(() => {
    const page = this.pageContent();

    return QUALITY_VISUAL_GUIDE_CONFIGS.map((guide) => {
      const translated = page.CALCULATOR.GUIDES.ITEMS[guide.id];

      return {
        id: guide.id,
        category: page.CALCULATOR.GUIDES.CATEGORIES[guide.categoryId],
        title: translated.TITLE,
        objectExample: translated.OBJECT_EXAMPLE,
        bestFor: translated.BEST_FOR,
        tradeoff: translated.TRADEOFF,
        calculatorRead: translated.CALCULATOR_READ,
        usageKey: guide.usageKey,
        image: this.resolveUsageImage(guide.usageKey),
      };
    });
  });

  readonly ringPolygons = computed(() => {
    const polygons: string[] = [];
    for (let level = 1; level <= CHART_LEVELS; level += 1) {
      polygons.push(this.polygonPoints(this.visualRatio(level / CHART_LEVELS)));
    }
    return polygons;
  });

  readonly axisGuides = computed<readonly AxisGuide[]>(() =>
    this.radarAxes().map((axis, index) => {
      const inner = this.pointForRatio(index, CHART_INNER_RATIO);
      const outer = this.pointForRatio(index, 1);
      const label = this.pointForRatio(index, 1.18);
      const anchor: 'start' | 'middle' | 'end' =
        Math.abs(label.x - CHART_CENTER) < 12
          ? 'middle'
          : label.x > CHART_CENTER
            ? 'start'
            : 'end';

      return {
        id: axis.id,
        fromX: inner.x,
        fromY: inner.y,
        x: outer.x,
        y: outer.y,
        labelX: label.x,
        labelY: label.y - (axis.labelLines.length - 1) * 6,
        labelAnchor: anchor,
        labelLines: axis.labelLines,
      };
    }),
  );

  readonly radarSeries = computed<readonly RadarSeries[]>(() => {
    const axes = this.radarAxes();

    return this.selectedMaterials().map((material) => {
      const style =
        this.selectedMaterialStyles().get(material.id) ?? SERIES_STYLES[0];
      const values: RadarPoint[] = axes.map((axis, axisIndex) => {
        const { rawValue, score } = this.axisScore(material, axis);
        const point = this.pointForScore(axisIndex, score);
        return {
          axis,
          score,
          rawValue,
          x: point.x,
          y: point.y,
        };
      });

      return {
        material,
        color: style.stroke,
        fill: style.fill,
        points: values.map((value) => `${value.x},${value.y}`).join(' '),
        values,
      };
    });
  });

  readonly comparisonRows = computed<readonly ComparisonRow[]>(() => {
    const page = this.pageContent();
    const selected = this.selectedMaterials();

    return COMPARISON_ROW_CONFIGS.map((config) => ({
      id: config.id,
      label: page.TABLE.ROWS[config.id],
      values: selected.map((material) => {
        const value = config.accessor(material);
        return typeof value === 'number'
          ? this.formatFixed(value, config.fractionDigits ?? 0)
          : value;
      }),
    }));
  });

  readonly allSources = computed<readonly MaterialSource[]>(() => {
    const unique = new Map<string, MaterialSource>();
    this.materials().forEach((material) => {
      material.sources.forEach((source) => {
        unique.set(source.url, source);
      });
    });
    return Array.from(unique.values());
  });

  isSelected(materialId: string): boolean {
    return this.selectedMaterialIds().includes(materialId);
  }

  canSelect(_materialId: string): boolean {
    return true;
  }

  toggleMaterial(materialId: string): void {
    const selected = this.selectedMaterialIds();

    if (selected.includes(materialId)) {
      const next = selected.filter((id) => id !== materialId);
      this.selectedMaterialIds.set(next.length > 0 ? next : [materialId]);
      return;
    }

    const next = [...selected, materialId];
    while (next.length > MAX_COMPARE_COUNT) {
      next.shift();
    }
    this.selectedMaterialIds.set(next);
  }

  setHoveredMaterial(materialId: string | null): void {
    this.hoveredMaterialId.set(materialId);
  }

  legendDotColor(materialId: string): string {
    return this.selectedMaterialStyles().get(materialId)?.stroke ?? '#9aa2ad';
  }

  legendFillColor(materialId: string): string {
    return this.selectedMaterialStyles().get(materialId)?.fill ?? '#ffffff';
  }

  trackMaterial(_index: number, material: MaterialRecord): string {
    return material.id;
  }

  trackSource(_index: number, source: MaterialSource): string {
    return source.url;
  }

  trackCalculatorFact(_index: number, fact: CalculatorFact): string {
    return fact.id;
  }

  trackCalculatorParameter(
    _index: number,
    parameter: CalculatorParameter,
  ): string {
    return parameter.id;
  }

  trackComparisonRow(_index: number, row: ComparisonRow): string {
    return row.id;
  }

  trackVisualGuide(_index: number, guide: QualityVisualCard): string {
    return guide.id;
  }

  localizedPath(path: string): string {
    return this.languageService.localizedPath(path);
  }

  private resolveUsageImage(
    usageKeyRaw: string,
  ): PublicMediaDisplayImage | null {
    const usageKey = buildPublicMediaUsageScopeKey(
      'MATERIALS_PAGE',
      usageKeyRaw,
    );
    const usageItems = this.mediaByUsage()[usageKey] ?? [];
    const primary = this.publicMediaService.pickPrimaryUsage(usageItems);
    return primary
      ? this.publicMediaService.toDisplayImage(primary, 'card')
      : null;
  }

  private axisScore(
    material: MaterialRecord,
    axis: RadarAxis,
  ): {
    rawValue: number;
    score: number;
  } {
    const rawValue = axis.accessor(material);
    const axisValues = this.materials().map(axis.accessor);
    const min = Math.min(...axisValues);
    const max = Math.max(...axisValues);

    if (max === min) {
      return { rawValue, score: 100 };
    }

    const normalized = ((rawValue - min) / (max - min)) * 100;
    const score = axis.lowerIsBetter ? 100 - normalized : normalized;
    return { rawValue, score: Math.max(0, Math.min(100, score)) };
  }

  private polygonPoints(ratio: number): string {
    return RADAR_AXIS_CONFIGS.map((_, index) => {
      const point = this.pointForRatio(index, ratio);
      return `${point.x},${point.y}`;
    }).join(' ');
  }

  private pointForScore(
    axisIndex: number,
    score: number,
  ): {
    x: number;
    y: number;
  } {
    return this.pointForRatio(axisIndex, this.visualRatio(score / 100));
  }

  private pointForRatio(
    axisIndex: number,
    ratio: number,
  ): {
    x: number;
    y: number;
  } {
    const angle =
      -Math.PI / 2 + (axisIndex * 2 * Math.PI) / RADAR_AXIS_CONFIGS.length;
    return {
      x: CHART_CENTER + Math.cos(angle) * CHART_RADIUS * ratio,
      y: CHART_CENTER + Math.sin(angle) * CHART_RADIUS * ratio,
    };
  }

  private visualRatio(normalizedRatio: number): number {
    const clampedRatio = this.clamp(normalizedRatio, 0, 1);
    return CHART_INNER_RATIO + clampedRatio * (1 - CHART_INNER_RATIO);
  }

  private clamp(value: number, min: number, max: number): number {
    return Math.min(Math.max(value, min), max);
  }

  private formatFixed(value: number, fractionDigits: number): string {
    return value.toFixed(fractionDigits);
  }

  protected readonly chartSize = CHART_SIZE;
}
