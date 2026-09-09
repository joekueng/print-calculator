import {
  Component,
  inject,
  OnInit,
  signal,
  Inject,
  Optional,
  PLATFORM_ID,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { QuoteEstimatorService } from '../calculator/services/quote-estimator.service';
import { AppInputComponent } from '../../shared/components/app-input/app-input.component';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import {
  AppToggleSelectorComponent,
  ToggleOption,
} from '../../shared/components/app-toggle-selector/app-toggle-selector.component';
import {
  PriceBreakdownComponent,
  PriceBreakdownRow,
} from '../../shared/components/price-breakdown/price-breakdown.component';
import { LanguageService } from '../../core/services/language.service';
import { StlViewerComponent } from '../../shared/components/stl-viewer/stl-viewer.component';
import {
  findColorHex,
  getColorHex,
  normalizeColorValue,
  resolveLocalizedColorLabel,
} from '../../core/constants/colors.const';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TranslateModule,
    AppInputComponent,
    AppButtonComponent,
    AppCardComponent,
    AppToggleSelectorComponent,
    PriceBreakdownComponent,
    StlViewerComponent,
  ],
  templateUrl: './checkout.component.html',
  styleUrls: ['./checkout.component.scss'],
})
export class CheckoutComponent implements OnInit {
  private fb = inject(FormBuilder);
  private quoteService = inject(QuoteEstimatorService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  readonly languageService = inject(LanguageService);
  private readonly isBrowser: boolean;

  checkoutForm: FormGroup;
  sessionId: string | null = null;
  loading = true;
  error: string | null = null;
  isSubmitting = signal(false); // Add signal for submit state
  quoteSession = signal<any>(null); // Add signal for session details
  previewFiles = signal<Record<string, File>>({});
  previewLoading = signal<Record<string, boolean>>({});
  previewErrors = signal<Record<string, boolean>>({});
  previewModalOpen = signal(false);
  selectedPreviewFile = signal<File | null>(null);
  selectedPreviewName = signal('');
  selectedPreviewColor = signal('#c9ced6');
  private variantHexById = new Map<number, string>();
  private variantHexByColorName = new Map<string, string>();

  userTypeOptions: ToggleOption[] = [
    { label: 'CONTACT.TYPE_PRIVATE', value: 'PRIVATE' },
    { label: 'CONTACT.TYPE_COMPANY', value: 'BUSINESS' },
  ];

  constructor(@Optional() @Inject(PLATFORM_ID) platformId?: Object) {
    this.isBrowser = isPlatformBrowser(platformId ?? 'browser');
    this.checkoutForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      phone: ['', Validators.required],
      customerType: ['PRIVATE', Validators.required], // Default to PRIVATE

      shippingSameAsBilling: [true],
      acceptLegal: [false, Validators.requiredTrue],

      billingAddress: this.fb.group({
        firstName: ['', Validators.required],
        lastName: ['', Validators.required],
        companyName: [''],
        referencePerson: [''],
        addressLine1: ['', Validators.required],
        addressLine2: [''],
        zip: ['', Validators.required],
        city: ['', Validators.required],
        countryCode: ['CH', Validators.required],
      }),

      shippingAddress: this.fb.group({
        firstName: [''],
        lastName: [''],
        companyName: [''],
        referencePerson: [''],
        addressLine1: [''],
        addressLine2: [''],
        zip: [''],
        city: [''],
        countryCode: ['CH'],
      }),
    });
  }

  get isCompany(): boolean {
    return this.checkoutForm.get('customerType')?.value === 'BUSINESS';
  }

  setCustomerType(type: string) {
    this.checkoutForm.patchValue({ customerType: type });
    const isCompany = type === 'BUSINESS';

    const billingGroup = this.checkoutForm.get('billingAddress') as FormGroup;
    const companyControl = billingGroup.get('companyName');
    const referenceControl = billingGroup.get('referencePerson');
    const firstNameControl = billingGroup.get('firstName');
    const lastNameControl = billingGroup.get('lastName');

    if (isCompany) {
      companyControl?.setValidators([Validators.required]);
      referenceControl?.setValidators([Validators.required]);
      firstNameControl?.clearValidators();
      lastNameControl?.clearValidators();
    } else {
      companyControl?.clearValidators();
      referenceControl?.clearValidators();
      firstNameControl?.setValidators([Validators.required]);
      lastNameControl?.setValidators([Validators.required]);
    }
    companyControl?.updateValueAndValidity();
    referenceControl?.updateValueAndValidity();
    firstNameControl?.updateValueAndValidity();
    lastNameControl?.updateValueAndValidity();
  }

  ngOnInit(): void {
    // Toggle shipping validation based on checkbox
    this.checkoutForm
      .get('shippingSameAsBilling')
      ?.valueChanges.subscribe((isSame) => {
        const shippingGroup = this.checkoutForm.get(
          'shippingAddress',
        ) as FormGroup;
        if (isSame) {
          shippingGroup.disable();
        } else {
          shippingGroup.enable();
        }
      });

    // Initial state
    this.checkoutForm.get('shippingAddress')?.disable();

    if (!this.isBrowser) {
      return;
    }

    this.loadMaterialColorPalette();

    this.route.queryParams.subscribe((params) => {
      this.sessionId = params['session'];
      if (!this.sessionId) {
        this.error = 'CHECKOUT.ERR_NO_SESSION_START';
        this.loading = false;
        this.router.navigate(['/', this.languageService.selectedLang()]);
        return;
      }

      this.loadSessionDetails();
    });
  }

  loadSessionDetails() {
    if (!this.sessionId) return; // Ensure sessionId is present before fetching
    this.loading = true;
    this.error = null;
    this.quoteSession.set(null);
    this.resetPreviewState();
    this.quoteService.getQuoteSession(this.sessionId).subscribe({
      next: (session) => {
        this.error = null;
        this.quoteSession.set(session);
        if (Array.isArray(session?.items) && session.items.length > 0) {
          this.loadStlPreviews(session);
        } else {
          this.resetPreviewState();
        }
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load session', err);
        this.quoteSession.set(null);
        this.resetPreviewState();
        this.error = 'CHECKOUT.ERR_LOAD_SESSION';
        this.loading = false;
      },
    });
  }

  orderableItems(session: any): any[] {
    return Array.isArray(session?.items)
      ? session.items.filter((item: any) => item?.status !== 'REVIEW_REQUIRED')
      : [];
  }

  isCadSession(): boolean {
    return this.isCadSessionData(this.quoteSession());
  }

  cadRequestId(): string | null {
    return this.quoteSession()?.session?.sourceRequestId ?? null;
  }

  cadHours(): number {
    return this.quoteSession()?.session?.cadHours ?? 0;
  }

  cadTotal(): number {
    return this.quoteSession()?.cadTotalChf ?? 0;
  }

  checkoutPriceBreakdownRows(session: any): PriceBreakdownRow[] {
    return [
      {
        labelKey: 'CHECKOUT.SUBTOTAL',
        amount: session?.itemsTotalChf ?? 0,
      },
      {
        labelKey: 'CHECKOUT.SETUP_FEE',
        amount:
          session?.baseSetupCostChf ?? session?.session?.setupCostChf ?? 0,
      },
      {
        labelKey: 'CHECKOUT.NOZZLE_CHANGE',
        amount: session?.nozzleChangeCostChf ?? 0,
        visible: (session?.nozzleChangeCostChf ?? 0) > 0,
      },
      {
        labelKey: 'CHECKOUT.SHIPPING',
        amount: session?.shippingCostChf ?? 0,
      },
    ];
  }

  itemMaterial(item: any): string {
    return String(
      item?.materialCode ?? this.quoteSession()?.session?.materialCode ?? '-',
    );
  }

  isShopItem(item: any): boolean {
    return String(item?.lineItemType ?? '').toUpperCase() === 'SHOP_PRODUCT';
  }

  itemDisplayName(item: any): string {
    const displayName = String(item?.displayName ?? '').trim();
    if (displayName) {
      return displayName;
    }
    const shopName = String(item?.shopProductName ?? '').trim();
    if (shopName) {
      return shopName;
    }
    return String(item?.originalFilename ?? '-');
  }

  itemVariantLabel(item: any): string | null {
    const variantLabel = String(item?.shopVariantLabel ?? '').trim();
    if (variantLabel) {
      return variantLabel;
    }
    return this.localizedShopColorLabel(item);
  }

  showItemMaterial(item: any): boolean {
    return !this.isShopItem(item);
  }

  showItemPrintMetrics(item: any): boolean {
    return !this.isShopItem(item);
  }

  isStlItem(item: any): boolean {
    const name = String(item?.originalFilename ?? '').toLowerCase();
    return name.endsWith('.stl');
  }

  previewFile(item: any): File | null {
    const id = String(item?.id ?? '');
    if (!id) {
      return null;
    }
    return this.previewFiles()[id] ?? null;
  }

  previewColor(item: any): string {
    return this.itemColorSwatch(item);
  }

  itemColorLabel(item: any): string {
    return this.localizedShopColorLabel(item) || String(item?.colorCode ?? '-');
  }

  itemColorSwatch(item: any): string {
    const shopHex = String(item?.shopVariantColorHex ?? '').trim();
    if (this.isHexColor(shopHex)) {
      return shopHex;
    }

    const variantId = Number(item?.filamentVariantId);
    if (Number.isFinite(variantId) && this.variantHexById.has(variantId)) {
      return this.variantHexById.get(variantId)!;
    }

    const raw = String(item?.colorCode ?? '').trim();
    if (!raw) {
      return '#c9ced6';
    }

    if (this.isHexColor(raw)) {
      return raw;
    }

    const byName = this.variantHexByColorName.get(normalizeColorValue(raw));
    if (byName) {
      return byName;
    }

    const fallback = findColorHex(raw) ?? getColorHex(raw);
    if (fallback && fallback !== '#facf0a') {
      return fallback;
    }

    return '#c9ced6';
  }

  isPreviewLoading(item: any): boolean {
    const id = String(item?.id ?? '');
    if (!id) {
      return false;
    }
    return !!this.previewLoading()[id];
  }

  private localizedShopColorLabel(item: any): string | null {
    return resolveLocalizedColorLabel(this.languageService.selectedLang(), {
      fallback: item?.shopVariantColorName ?? item?.colorCode,
      it: item?.shopVariantColorLabelIt,
      en: item?.shopVariantColorLabelEn,
      de: item?.shopVariantColorLabelDe,
      fr: item?.shopVariantColorLabelFr,
    });
  }

  hasPreviewError(item: any): boolean {
    const id = String(item?.id ?? '');
    if (!id) {
      return false;
    }
    return !!this.previewErrors()[id];
  }

  openPreview(item: any): void {
    const file = this.previewFile(item);
    if (!file) {
      return;
    }
    this.selectedPreviewFile.set(file);
    this.selectedPreviewName.set(this.itemDisplayName(item));
    this.selectedPreviewColor.set(this.previewColor(item));
    this.previewModalOpen.set(true);
  }

  closePreview(): void {
    this.previewModalOpen.set(false);
    this.selectedPreviewFile.set(null);
    this.selectedPreviewName.set('');
    this.selectedPreviewColor.set('#c9ced6');
  }

  private loadMaterialColorPalette(): void {
    this.quoteService.getOptions().subscribe({
      next: (options) => {
        this.variantHexById.clear();
        this.variantHexByColorName.clear();

        for (const material of options?.materials || []) {
          for (const variant of material?.variants || []) {
            const variantId = Number(variant?.id);
            const colorHex = String(variant?.hexColor || '').trim();
            const colorName = String(variant?.colorName || '').trim();

            if (Number.isFinite(variantId) && colorHex) {
              this.variantHexById.set(variantId, colorHex);
            }
            if (colorName && colorHex) {
              this.variantHexByColorName.set(
                normalizeColorValue(colorName),
                colorHex,
              );
            }
          }
        }
      },
      error: () => {
        this.variantHexById.clear();
        this.variantHexByColorName.clear();
      },
    });
  }

  private isHexColor(value?: string): boolean {
    return (
      typeof value === 'string' &&
      /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(value)
    );
  }

  private loadStlPreviews(session: any): void {
    if (!this.sessionId || !Array.isArray(session?.items)) {
      return;
    }

    for (const item of session.items) {
      if (!this.isStlItem(item)) {
        continue;
      }

      const id = String(item?.id ?? '');
      if (!id || this.previewFiles()[id] || this.previewLoading()[id]) {
        continue;
      }

      this.previewLoading.update((prev) => ({ ...prev, [id]: true }));
      this.previewErrors.update((prev) => ({ ...prev, [id]: false }));

      this.quoteService.getLineItemStlPreview(this.sessionId, id).subscribe({
        next: (blob) => {
          const originalName = String(item?.originalFilename ?? `${id}.stl`);
          const stlName = originalName.toLowerCase().endsWith('.stl')
            ? originalName
            : `${originalName}.stl`;
          const previewFile = new File([blob], stlName, { type: 'model/stl' });
          this.previewFiles.update((prev) => ({ ...prev, [id]: previewFile }));
          this.previewLoading.update((prev) => ({ ...prev, [id]: false }));
        },
        error: () => {
          this.previewErrors.update((prev) => ({ ...prev, [id]: true }));
          this.previewLoading.update((prev) => ({ ...prev, [id]: false }));
        },
      });
    }
  }

  private isCadSessionData(session: any): boolean {
    return session?.session?.status === 'CAD_ACTIVE';
  }

  private resetPreviewState(): void {
    this.previewFiles.set({});
    this.previewLoading.set({});
    this.previewErrors.set({});
    this.closePreview();
  }

  onSubmit() {
    if (this.checkoutForm.invalid) {
      return;
    }

    this.isSubmitting.set(true);
    this.error = null; // Clear previous errors
    const formVal = this.checkoutForm.getRawValue(); // Use getRawValue to include disabled fields

    // Construct request object matching backend DTO based on original form structure
    const orderRequest = {
      customer: {
        email: formVal.email,
        phone: formVal.phone,
        customerType: formVal.customerType,
        // Assuming firstName, lastName, companyName for customer come from billingAddress if not explicitly in contact group
        firstName: formVal.billingAddress.firstName,
        lastName: formVal.billingAddress.lastName,
        companyName: formVal.billingAddress.companyName,
      },
      billingAddress: {
        firstName: formVal.billingAddress.firstName,
        lastName: formVal.billingAddress.lastName,
        companyName: formVal.billingAddress.companyName,
        contactPerson: formVal.billingAddress.referencePerson,
        addressLine1: formVal.billingAddress.addressLine1,
        addressLine2: formVal.billingAddress.addressLine2,
        zip: formVal.billingAddress.zip,
        city: formVal.billingAddress.city,
        countryCode: formVal.billingAddress.countryCode,
      },
      shippingAddress: formVal.shippingSameAsBilling
        ? null
        : {
            firstName: formVal.shippingAddress.firstName,
            lastName: formVal.shippingAddress.lastName,
            companyName: formVal.shippingAddress.companyName,
            contactPerson: formVal.shippingAddress.referencePerson,
            addressLine1: formVal.shippingAddress.addressLine1,
            addressLine2: formVal.shippingAddress.addressLine2,
            zip: formVal.shippingAddress.zip,
            city: formVal.shippingAddress.city,
            countryCode: formVal.shippingAddress.countryCode,
          },
      shippingSameAsBilling: formVal.shippingSameAsBilling,
      language: this.languageService.selectedLang(),
      acceptTerms: formVal.acceptLegal,
      acceptPrivacy: formVal.acceptLegal,
    };

    if (!this.sessionId) {
      this.error = 'CHECKOUT.ERR_NO_SESSION_CREATE_ORDER';
      this.isSubmitting.set(false);
      return;
    }

    this.quoteService.createOrder(this.sessionId, orderRequest).subscribe({
      next: (order) => {
        const orderId = order?.id ?? order?.orderId;
        if (!orderId) {
          this.isSubmitting.set(false);
          this.error = 'CHECKOUT.ERR_CREATE_ORDER';
          return;
        }
        this.router.navigate([
          '/',
          this.languageService.selectedLang(),
          'order',
          orderId,
        ]);
      },
      error: (err) => {
        console.error('Order creation failed', err);
        this.isSubmitting.set(false);
        this.error = 'CHECKOUT.ERR_CREATE_ORDER';
      },
    });
  }
}
