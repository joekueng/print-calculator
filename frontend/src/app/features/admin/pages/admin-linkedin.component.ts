import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { AppButtonComponent } from '../../../shared/components/app-button/app-button.component';
import {
  AdminLinkedInService,
  AdminLinkedInSyncStatus,
} from '../services/admin-linkedin.service';

@Component({
  selector: 'app-admin-linkedin',
  standalone: true,
  imports: [CommonModule, TranslateModule, AppButtonComponent],
  templateUrl: './admin-linkedin.component.html',
  styleUrl: './admin-linkedin.component.scss',
})
export class AdminLinkedInComponent implements OnInit {
  private readonly linkedInService = inject(AdminLinkedInService);

  status: AdminLinkedInSyncStatus | null = null;
  loading = false;
  syncing = false;
  messageKey: string | null = null;
  messageIsError = false;

  ngOnInit(): void {
    this.loadStatus();
  }

  loadStatus(): void {
    this.loading = true;
    this.messageKey = null;
    this.linkedInService.getStatus().subscribe({
      next: (status) => {
        this.status = status;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.showMessage('ADMIN_LINKEDIN.STATUS_ERROR', true);
      },
    });
  }

  refresh(): void {
    if (this.syncing) {
      return;
    }
    this.syncing = true;
    this.messageKey = null;
    this.linkedInService.refresh().subscribe({
      next: (status) => {
        this.status = status;
        this.syncing = false;
        if (!status.enabled || !status.configured) {
          this.showMessage('ADMIN_LINKEDIN.CONFIG_REQUIRED', true);
        } else if (status.updated) {
          this.showMessage('ADMIN_LINKEDIN.SYNC_SUCCESS', false);
        } else {
          this.showMessage('ADMIN_LINKEDIN.SYNC_ERROR', true);
        }
      },
      error: () => {
        this.syncing = false;
        this.showMessage('ADMIN_LINKEDIN.SYNC_ERROR', true);
      },
    });
  }

  private showMessage(key: string, isError: boolean): void {
    this.messageKey = key;
    this.messageIsError = isError;
  }
}
