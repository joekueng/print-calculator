import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Component, OnInit, PLATFORM_ID, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AdminCadInvoice,
  AdminOperationsService,
} from '../services/admin-operations.service';
import { AdminOrdersService } from '../services/admin-orders.service';
import { CopyOnClickDirective } from '../../../shared/directives/copy-on-click.directive';
import { AppButtonComponent } from '../../../shared/components/app-button/app-button.component';
import { AppInputComponent } from '../../../shared/components/app-input/app-input.component';
import { AppTextareaComponent } from '../../../shared/components/app-textarea/app-textarea.component';
import { downloadBlobInBrowser } from '../../../core/utils/browser-download';

export function parseDecimalInput(value: unknown): number {
  const normalized = String(value ?? '')
    .trim()
    .replace(',', '.');

  if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(normalized)) {
    return Number.NaN;
  }

  return Number(normalized);
}

@Component({
  selector: 'app-admin-cad-invoices',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CopyOnClickDirective,
    AppButtonComponent,
    AppInputComponent,
    AppTextareaComponent,
  ],
  templateUrl: './admin-cad-invoices.component.html',
  styleUrl: './admin-cad-invoices.component.scss',
})
export class AdminCadInvoicesComponent implements OnInit {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly adminOperationsService = inject(AdminOperationsService);
  private readonly adminOrdersService = inject(AdminOrdersService);

  invoices: AdminCadInvoice[] = [];
  loading = false;
  creating = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  form = {
    sessionId: '',
    sourceRequestId: '',
    cadHours: '1',
    cadHourlyRateChf: '',
    notes: '',
  };

  ngOnInit(): void {
    this.loadCadInvoices();
  }

  loadCadInvoices(): void {
    this.loading = true;
    this.errorMessage = null;
    this.adminOperationsService.listCadInvoices().subscribe({
      next: (rows) => {
        this.invoices = rows;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Impossibile caricare le fatture CAD.';
      },
    });
  }

  createCadInvoice(): void {
    if (this.creating) {
      return;
    }

    const cadHours = parseDecimalInput(this.form.cadHours);
    if (!Number.isFinite(cadHours) || cadHours <= 0) {
      this.errorMessage = 'Inserisci ore CAD valide (> 0).';
      return;
    }

    this.creating = true;
    this.errorMessage = null;
    this.successMessage = null;

    let payload: {
      sessionId?: string;
      sourceRequestId?: string;
      cadHours: number;
      cadHourlyRateChf?: number;
      notes?: string;
    };

    try {
      const sessionIdRaw = String(this.form.sessionId ?? '').trim();
      const sourceRequestIdRaw = String(this.form.sourceRequestId ?? '').trim();
      const cadRateRaw = String(this.form.cadHourlyRateChf ?? '').trim();
      const notesRaw = String(this.form.notes ?? '').trim();
      const cadHourlyRateChf =
        cadRateRaw.length > 0 ? parseDecimalInput(cadRateRaw) : undefined;

      if (
        cadHourlyRateChf !== undefined &&
        (!Number.isFinite(cadHourlyRateChf) || cadHourlyRateChf < 0)
      ) {
        this.creating = false;
        this.errorMessage = 'Inserisci una tariffa CAD valida (>= 0).';
        return;
      }

      payload = {
        sessionId: sessionIdRaw || undefined,
        sourceRequestId: sourceRequestIdRaw || undefined,
        cadHours,
        cadHourlyRateChf,
        notes: notesRaw.length > 0 ? notesRaw : undefined,
      };
    } catch {
      this.creating = false;
      this.errorMessage = 'Valori form non validi.';
      return;
    }

    this.adminOperationsService.createCadInvoice(payload).subscribe({
      next: (created) => {
        this.creating = false;
        this.successMessage = `Fattura CAD pronta. Sessione: ${created.sessionId}`;
        this.loadCadInvoices();
      },
      error: (err) => {
        this.creating = false;
        this.errorMessage =
          err?.error?.message || 'Creazione fattura CAD non riuscita.';
      },
    });
  }

  openCheckout(path: string): void {
    if (!this.isBrowser) {
      return;
    }
    const url = this.toCheckoutUrl(path);
    window.open(url, '_blank');
  }

  copyCheckout(path: string): void {
    if (!this.isBrowser) {
      return;
    }
    const url = this.toCheckoutUrl(path);
    navigator.clipboard?.writeText(url);
    this.successMessage = 'Link checkout CAD copiato negli appunti.';
  }

  downloadInvoice(orderId?: string): void {
    if (!orderId) return;
    this.adminOrdersService.downloadOrderInvoice(orderId).subscribe({
      next: (blob) => {
        if (!this.isBrowser) {
          return;
        }
        downloadBlobInBrowser(blob, `fattura-cad-${orderId}.pdf`);
      },
      error: () => {
        this.errorMessage = 'Download fattura non riuscito.';
      },
    });
  }

  private toCheckoutUrl(path: string): string {
    const safePath = path.startsWith('/') ? path : `/${path}`;
    const lang = this.resolveLang();
    if (!this.isBrowser) {
      return `/${lang}${safePath}`;
    }
    return `${window.location.origin}/${lang}${safePath}`;
  }

  private resolveLang(): string {
    if (!this.isBrowser) {
      return 'it';
    }
    const firstSegment = window.location.pathname
      .split('/')
      .filter(Boolean)
      .shift();
    if (firstSegment && ['it', 'en', 'de', 'fr'].includes(firstSegment)) {
      return firstSegment;
    }
    return 'it';
  }
}
