import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Component, PLATFORM_ID, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AdminContactRequest,
  AdminContactRequestAttachment,
  AdminContactRequestDetail,
  AdminOperationsService,
} from '../services/admin-operations.service';
import { AdminEmailLog } from '../services/admin-email-log.model';
import { CopyOnClickDirective } from '../../../shared/directives/copy-on-click.directive';
import { AppButtonComponent } from '../../../shared/components/app-button/app-button.component';
import { AppSelectComponent } from '../../../shared/components/app-select/app-select.component';
import { downloadBlobInBrowser } from '../../../core/utils/browser-download';

@Component({
  selector: 'app-admin-contact-requests',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CopyOnClickDirective,
    AppButtonComponent,
    AppSelectComponent,
  ],
  templateUrl: './admin-contact-requests.component.html',
  styleUrl: './admin-contact-requests.component.scss',
})
export class AdminContactRequestsComponent implements OnInit {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly adminOperationsService = inject(AdminOperationsService);

  readonly statusOptions = ['NEW', 'PENDING', 'IN_PROGRESS', 'DONE', 'CLOSED'];
  readonly statusSelectOptions = this.statusOptions.map((status) => ({
    label: status,
    value: status,
  }));
  requests: AdminContactRequest[] = [];
  selectedRequest: AdminContactRequestDetail | null = null;
  selectedRequestId: string | null = null;
  loading = false;
  detailLoading = false;
  updatingStatus = false;
  selectedStatus = '';
  errorMessage: string | null = null;
  successMessage: string | null = null;
  resendingEmailLogIds = new Set<string>();

  ngOnInit(): void {
    this.loadRequests();
  }

  loadRequests(): void {
    this.loading = true;
    this.errorMessage = null;
    this.successMessage = null;
    this.adminOperationsService.getContactRequests().subscribe({
      next: (requests) => {
        this.requests = requests;
        if (requests.length === 0) {
          this.selectedRequest = null;
          this.selectedRequestId = null;
        } else if (
          this.selectedRequestId &&
          requests.some((r) => r.id === this.selectedRequestId)
        ) {
          this.openDetails(this.selectedRequestId);
        } else {
          this.openDetails(requests[0].id);
        }
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Impossibile caricare le richieste di contatto.';
      },
    });
  }

  openDetails(requestId: string): void {
    this.selectedRequestId = requestId;
    this.detailLoading = true;
    this.errorMessage = null;
    this.adminOperationsService.getContactRequestDetail(requestId).subscribe({
      next: (detail) => {
        this.selectedRequest = detail;
        this.selectedStatus = detail.status || '';
        this.detailLoading = false;
      },
      error: () => {
        this.detailLoading = false;
        this.errorMessage = 'Impossibile caricare il dettaglio richiesta.';
      },
    });
  }

  isSelected(requestId: string): boolean {
    return this.selectedRequestId === requestId;
  }

  downloadAttachment(attachment: AdminContactRequestAttachment): void {
    if (!this.selectedRequest) {
      return;
    }

    this.adminOperationsService
      .downloadContactRequestAttachment(this.selectedRequest.id, attachment.id)
      .subscribe({
        next: (blob) =>
          this.downloadBlob(
            blob,
            attachment.originalFilename || `attachment-${attachment.id}`,
          ),
        error: () => {
          this.errorMessage = 'Download allegato non riuscito.';
        },
      });
  }

  formatFileSize(bytes?: number): string {
    if (!bytes || bytes <= 0) {
      return '-';
    }
    const units = ['B', 'KB', 'MB', 'GB'];
    let value = bytes;
    let unitIndex = 0;
    while (value >= 1024 && unitIndex < units.length - 1) {
      value /= 1024;
      unitIndex += 1;
    }
    return `${value.toFixed(value >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
  }

  getStatusChipClass(status?: string): string {
    const normalized = (status || '').trim().toUpperCase();
    if (['PENDING', 'NEW', 'OPEN', 'IN_PROGRESS'].includes(normalized)) {
      return 'ui-status-chip--warning';
    }
    if (['DONE', 'COMPLETED', 'RESOLVED', 'CLOSED'].includes(normalized)) {
      return 'ui-status-chip--success';
    }
    if (['REJECTED', 'FAILED', 'ERROR', 'SPAM'].includes(normalized)) {
      return 'ui-status-chip--danger';
    }
    return 'ui-status-chip--neutral';
  }

  updateRequestStatus(): void {
    if (
      !this.selectedRequest ||
      !this.selectedRequestId ||
      !this.selectedStatus ||
      this.updatingStatus
    ) {
      return;
    }

    this.errorMessage = null;
    this.successMessage = null;
    this.updatingStatus = true;

    this.adminOperationsService
      .updateContactRequestStatus(this.selectedRequestId, {
        status: this.selectedStatus,
      })
      .subscribe({
        next: (updated) => {
          this.selectedRequest = updated;
          this.selectedStatus = updated.status || this.selectedStatus;
          this.requests = this.requests.map((request) =>
            request.id === updated.id
              ? {
                  ...request,
                  status: updated.status,
                }
              : request,
          );
          this.updatingStatus = false;
          this.successMessage = 'Stato richiesta aggiornato.';
        },
        error: () => {
          this.updatingStatus = false;
          this.errorMessage =
            'Impossibile aggiornare lo stato della richiesta.';
        },
      });
  }

  resendEmail(log: AdminEmailLog): void {
    if (
      !this.selectedRequest ||
      !this.selectedRequestId ||
      this.resendingEmailLogIds.has(log.id)
    ) {
      return;
    }

    this.errorMessage = null;
    this.successMessage = null;
    this.resendingEmailLogIds.add(log.id);
    this.adminOperationsService
      .resendContactRequestEmail(this.selectedRequestId, log.id)
      .subscribe({
        next: (updated) => {
          this.resendingEmailLogIds.delete(log.id);
          this.selectedRequest = updated;
          this.selectedStatus = updated.status || this.selectedStatus;
          this.successMessage = 'Email reinviata.';
        },
        error: () => {
          this.resendingEmailLogIds.delete(log.id);
          this.errorMessage = 'Reinvio email non riuscito.';
        },
      });
  }

  emailLogs(request: AdminContactRequestDetail | null): AdminEmailLog[] {
    return request?.emailLogs ?? [];
  }

  emailEventLabel(eventType?: string): string {
    switch ((eventType || '').trim().toUpperCase()) {
      case 'CONTACT_REQUEST_ADMIN':
        return 'Notifica richiesta admin';
      case 'CONTACT_REQUEST_CUSTOMER':
        return 'Conferma richiesta cliente';
      default:
        return eventType || '-';
    }
  }

  emailStatusLabel(status?: string): string {
    switch ((status || '').trim().toUpperCase()) {
      case 'SENT':
        return 'Inviata';
      case 'FAILED':
        return 'Fallita';
      case 'SKIPPED':
        return 'Saltata';
      default:
        return status || '-';
    }
  }

  emailStatusClass(status?: string): string {
    switch ((status || '').trim().toUpperCase()) {
      case 'SENT':
        return 'email-status--sent';
      case 'FAILED':
        return 'email-status--failed';
      case 'SKIPPED':
        return 'email-status--skipped';
      default:
        return 'email-status--neutral';
    }
  }

  isResendingEmailLog(emailLogId: string): boolean {
    return this.resendingEmailLogIds.has(emailLogId);
  }

  private downloadBlob(blob: Blob, filename: string): void {
    if (!this.isBrowser) {
      return;
    }
    downloadBlobInBrowser(blob, filename);
  }
}
