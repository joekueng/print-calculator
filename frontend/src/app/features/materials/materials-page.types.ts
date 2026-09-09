import { PublicMediaDisplayImage } from '../../core/services/public-media.service';

export type MaterialId =
  | 'tpu-95a-hf'
  | 'pla-basic'
  | 'pla-matte'
  | 'pla-tough-plus'
  | 'asa'
  | 'pc'
  | 'pa12-cf'
  | 'petg-extrudr'
  | 'pet-cf';

export type MaterialSourceKindId = 'wikipedia' | 'tech-sheet' | 'product-sheet';

export interface MaterialSourceConfig {
  id: string;
  kindId: MaterialSourceKindId;
  url: string;
}

export interface MaterialSource {
  id: string;
  label: string;
  kind: string;
  url: string;
}

export interface MaterialMetrics {
  priceChfKg: number;
  densityGcm3: number;
  tensileMpa: number;
  modulusGpa: number;
  elongationPct: number;
  hdtC: number;
  extrusionC: string;
  printability: number;
  layerRangeMm: string;
}

export interface MaterialConfig {
  id: MaterialId;
  metrics: MaterialMetrics;
  sources: readonly MaterialSourceConfig[];
}

export interface MaterialRecord {
  id: MaterialId;
  name: string;
  summary: string;
  qualityTips: readonly string[];
  metrics: MaterialMetrics;
  pros: readonly string[];
  cons: readonly string[];
  idealFor: readonly string[];
  sources: readonly MaterialSource[];
}

export type RadarAxisId =
  | 'economy'
  | 'printability'
  | 'tensile'
  | 'modulus'
  | 'elongation'
  | 'hdt';

export interface RadarAxisConfig {
  id: RadarAxisId;
  lowerIsBetter?: boolean;
  accessor: (material: MaterialRecord) => number;
}

export interface RadarAxis {
  id: RadarAxisId;
  label: string;
  labelLines: readonly string[];
  description: string;
  unit: string;
  lowerIsBetter?: boolean;
  accessor: (material: MaterialRecord) => number;
}

export interface RadarPoint {
  axis: RadarAxis;
  score: number;
  rawValue: number;
  x: number;
  y: number;
}

export interface RadarSeries {
  material: MaterialRecord;
  color: string;
  fill: string;
  points: string;
  values: readonly RadarPoint[];
}

export interface AxisGuide {
  id: RadarAxisId;
  fromX: number;
  fromY: number;
  x: number;
  y: number;
  labelX: number;
  labelY: number;
  labelAnchor: 'start' | 'middle' | 'end';
  labelLines: readonly string[];
}

export type ComparisonRowId =
  | 'printability'
  | 'layer-range'
  | 'price'
  | 'density'
  | 'tensile'
  | 'modulus'
  | 'elongation'
  | 'hdt'
  | 'extrusion';

export interface ComparisonRowConfig {
  id: ComparisonRowId;
  accessor: (material: MaterialRecord) => number | string;
  fractionDigits?: number;
}

export interface ComparisonRow {
  id: ComparisonRowId;
  label: string;
  values: readonly string[];
}

export type CalculatorFactId = 'overview' | 'basic' | 'advanced';

export interface CalculatorFactConfig {
  id: CalculatorFactId;
  path?: string;
}

export interface CalculatorFact {
  id: CalculatorFactId;
  eyebrow: string;
  title: string;
  description: string;
  detailLabel?: string;
  detail?: string;
  noteLabel?: string;
  note?: string;
  ctaLabel?: string;
  path?: string;
}

export type CalculatorParameterId =
  | 'material'
  | 'quality'
  | 'nozzleDiameter'
  | 'layerHeight'
  | 'infill'
  | 'infillPattern'
  | 'supports';

export interface CalculatorParameterConfig {
  id: CalculatorParameterId;
}

export interface CalculatorParameter {
  id: CalculatorParameterId;
  title: string;
  availability: string;
  explanation: string;
  calculatorEffect: string;
}

export type GuideCategoryId = 'layer' | 'nozzles' | 'infill';

export type QualityVisualGuideId =
  | 'layer-012'
  | 'layer-020'
  | 'layer-028'
  | 'nozzles-040-060'
  | 'infill-15'
  | 'infill-40';

export interface QualityVisualGuideConfig {
  id: QualityVisualGuideId;
  categoryId: GuideCategoryId;
  usageKey: string;
}

export interface QualityVisualGuide {
  id: QualityVisualGuideId;
  category: string;
  title: string;
  objectExample: string;
  bestFor: string;
  tradeoff: string;
  calculatorRead: string;
  usageKey: string;
}

export interface QualityVisualCard extends QualityVisualGuide {
  image: PublicMediaDisplayImage | null;
}

export interface MaterialSeriesStyle {
  stroke: string;
  fill: string;
}

export interface MaterialSourceTranslation {
  LABEL: string;
}

export interface MaterialTranslation {
  NAME: string;
  SUMMARY: string;
  QUALITY_TIPS: readonly string[];
  PROS: readonly string[];
  CONS: readonly string[];
  IDEAL_FOR: readonly string[];
  SOURCES: Record<string, MaterialSourceTranslation>;
}

export interface RadarAxisTranslation {
  LABEL: string;
  LABEL_LINES?: readonly string[];
  DESCRIPTION: string;
  UNIT: string;
}

export interface CalculatorFactTranslation {
  EYEBROW: string;
  TITLE: string;
  DESCRIPTION: string;
  DETAIL_LABEL?: string;
  DETAIL?: string;
  NOTE_LABEL?: string;
  NOTE?: string;
  CTA_LABEL?: string;
}

export interface CalculatorParameterTranslation {
  TITLE: string;
  AVAILABILITY: string;
  EXPLANATION: string;
  CALCULATOR_EFFECT: string;
}

export interface QualityVisualGuideTranslation {
  TITLE: string;
  OBJECT_EXAMPLE: string;
  BEST_FOR: string;
  TRADEOFF: string;
  CALCULATOR_READ: string;
}

export interface MaterialsPageTranslations {
  HERO: {
    TITLE: string;
    SUBTITLE_PREFIX: string;
    LINK_LABEL: string;
    SUBTITLE_SUFFIX: string;
  };
  RADAR: {
    EYEBROW: string;
    TITLE: string;
    DESCRIPTION: string;
    ARIA_LABEL: string;
    SELECTOR_TITLE: string;
    SELECTOR_ARIA_LABEL: string;
    SELECTOR_HELP: string;
    READING_TITLE: string;
    READING_DESCRIPTION: string;
    AXES: Record<RadarAxisId, RadarAxisTranslation>;
  };
  TABLE: {
    EYEBROW: string;
    TITLE: string;
    PARAMETER_HEADER: string;
    ROWS: Record<ComparisonRowId, string>;
  };
  CALCULATOR: {
    SECTION_EYEBROW: string;
    SECTION_TITLE: string;
    SECTION_DESCRIPTION: string;
    FACTS: Record<CalculatorFactId, CalculatorFactTranslation>;
    PARAMETERS: {
      TITLE: string;
      DESCRIPTION: string;
      EFFECT_LABEL: string;
      ITEMS: Record<CalculatorParameterId, CalculatorParameterTranslation>;
    };
    GUIDES: {
      TITLE: string;
      DESCRIPTION: string;
      FALLBACK_IMAGE: string;
      SPEC_LABELS: {
        OBJECT_EXAMPLE: string;
        BEST_FOR: string;
        TRADEOFF: string;
        CALCULATOR_READ: string;
      };
      CATEGORIES: Record<GuideCategoryId, string>;
      ITEMS: Record<QualityVisualGuideId, QualityVisualGuideTranslation>;
    };
  };
  SOURCES: {
    EYEBROW: string;
    TITLE: string;
    DESCRIPTION: string;
    KINDS: Record<MaterialSourceKindId, string>;
  };
  MATERIALS: Record<MaterialId, MaterialTranslation>;
}
