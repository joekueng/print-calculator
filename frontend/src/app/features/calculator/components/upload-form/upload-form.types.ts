export interface FormItem {
  clientKey: string;
  file: File;
  previewFile?: File;
  quantity: number;
  material: string;
  quality: string;
  color: string;
  filamentVariantId?: number;
  supportEnabled: boolean;
  infillDensity: number;
  infillPattern: string;
  layerHeight: number;
  nozzleDiameter: number;
  reviewLevel?: 'warning' | 'error';
  reviewMessage?: string;
}

export interface ItemSettingsDiffInfo {
  differences: string[];
}

export type ItemPrintSettingsUpdate = Partial<
  Pick<
    FormItem,
    | 'material'
    | 'quality'
    | 'nozzleDiameter'
    | 'layerHeight'
    | 'infillDensity'
    | 'infillPattern'
    | 'supportEnabled'
  >
>;

export interface PrintSettingsSnapshot {
  material: string;
  quality: string;
  nozzleDiameter: number;
  layerHeight: number;
  infillDensity: number;
  infillPattern: string;
  supportEnabled: boolean;
}

export interface EasyModePreset {
  quality: string;
  nozzleDiameter: number;
  layerHeight: number;
  infillDensity: number;
  infillPattern: string;
}
