import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AdminEmailLog } from './admin-email-log.model';

export interface AdminOrderItem {
  id: string;
  itemType: string;
  originalFilename: string;
  displayName?: string;
  materialCode: string;
  colorCode: string;
  filamentVariantId?: number;
  shopProductId?: string;
  shopProductVariantId?: string;
  shopProductSlug?: string;
  shopProductName?: string;
  shopVariantLabel?: string;
  shopVariantColorName?: string;
  shopVariantColorHex?: string;
  filamentVariantDisplayName?: string;
  filamentColorName?: string;
  filamentColorHex?: string;
  quality?: string;
  nozzleDiameterMm?: number;
  layerHeightMm?: number;
  infillPercent?: number;
  infillPattern?: string;
  supportsEnabled?: boolean;
  requiresSplitPrinting?: boolean;
  quantity: number;
  printTimeSeconds: number;
  materialGrams: number;
  unitPriceChf: number;
  lineTotalChf: number;
}

export interface AdminOrderCadFile {
  id: string;
  originalFilename: string;
  fileSizeBytes?: number;
  mimeType?: string;
  createdAt: string;
}

export interface AdminOrderAddress {
  firstName?: string | null;
  lastName?: string | null;
  companyName?: string | null;
  contactPerson?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  zip?: string | null;
  city?: string | null;
  countryCode?: string | null;
}

export interface AdminOrder {
  id: string;
  orderNumber: string;
  sourceType?: string | null;
  status: string;
  paymentStatus?: string | null;
  paymentMethod?: string | null;
  billingCustomerType?: string | null;
  customerEmail: string;
  customerPhone?: string | null;
  preferredLanguage?: string | null;
  billingAddress?: AdminOrderAddress | null;
  shippingAddress?: AdminOrderAddress | null;
  shippingSameAsBilling?: boolean | null;
  currency?: string | null;
  setupCostChf?: number;
  shippingCostChf?: number;
  discountChf?: number;
  subtotalChf?: number;
  totalChf: number;
  createdAt: string;
  paidAt?: string;
  isCadOrder?: boolean;
  sourceRequestId?: string;
  cadHours?: number;
  cadHourlyRateChf?: number;
  cadTotalChf?: number;
  cadFileCount?: number;
  cadFileDownloadAvailable?: boolean;
  cadFiles?: AdminOrderCadFile[];
  printMaterialCode?: string;
  printNozzleDiameterMm?: number;
  printLayerHeightMm?: number;
  printInfillPattern?: string;
  printInfillPercent?: number;
  printSupportsEnabled?: boolean;
  emailLogs: AdminEmailLog[];
  items: AdminOrderItem[];
}

export interface AdminOrderStatistics {
  paidOrderCount: number;
  revenueChf: number;
  averageOrderValueChf: number;
  uniqueCustomerCount: number;
}

export interface AdminUpdateOrderStatusPayload {
  status: string;
}

@Injectable({
  providedIn: 'root',
})
export class AdminOrdersService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/admin/orders`;

  listOrders(): Observable<AdminOrder[]> {
    return this.http.get<AdminOrder[]>(this.baseUrl, { withCredentials: true });
  }

  getStatistics(): Observable<AdminOrderStatistics> {
    return this.http.get<AdminOrderStatistics>(`${this.baseUrl}/statistics`, {
      withCredentials: true,
    });
  }

  getOrder(orderId: string): Observable<AdminOrder> {
    return this.http.get<AdminOrder>(`${this.baseUrl}/${orderId}`, {
      withCredentials: true,
    });
  }

  updatePaymentMethod(orderId: string, method: string): Observable<AdminOrder> {
    return this.http.patch<AdminOrder>(
      `${this.baseUrl}/${orderId}/payments/method`,
      { method },
      { withCredentials: true },
    );
  }

  updateOrderStatus(
    orderId: string,
    payload: AdminUpdateOrderStatusPayload,
  ): Observable<AdminOrder> {
    return this.http.post<AdminOrder>(
      `${this.baseUrl}/${orderId}/status`,
      payload,
      { withCredentials: true },
    );
  }

  resendEmail(orderId: string, emailLogId: string): Observable<AdminOrder> {
    return this.http.post<AdminOrder>(
      `${this.baseUrl}/${orderId}/email-logs/${emailLogId}/resend`,
      {},
      { withCredentials: true },
    );
  }

  downloadOrderItemFile(
    orderId: string,
    orderItemId: string,
  ): Observable<Blob> {
    return this.http.get(
      `${this.baseUrl}/${orderId}/items/${orderItemId}/file`,
      {
        withCredentials: true,
        responseType: 'blob',
      },
    );
  }

  downloadOrderConfirmation(orderId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${orderId}/documents/confirmation`, {
      withCredentials: true,
      responseType: 'blob',
    });
  }

  downloadOrderInvoice(orderId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${orderId}/documents/invoice`, {
      withCredentials: true,
      responseType: 'blob',
    });
  }

  uploadCadFiles(orderId: string, files: File[]): Observable<AdminOrder> {
    const formData = new FormData();
    for (const file of files) {
      formData.append('files', file);
    }

    return this.http.post<AdminOrder>(
      `${this.baseUrl}/${orderId}/cad-files`,
      formData,
      { withCredentials: true },
    );
  }

  deleteCadFile(orderId: string, fileId: string): Observable<AdminOrder> {
    return this.http.delete<AdminOrder>(
      `${this.baseUrl}/${orderId}/cad-files/${fileId}`,
      { withCredentials: true },
    );
  }
}
