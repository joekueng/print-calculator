export interface AdminEmailLog {
  id: string;
  scope: 'ORDER' | 'CONTACT_REQUEST' | string;
  eventType: string;
  origin: 'SYSTEM' | 'ADMIN' | string;
  status: 'SENT' | 'FAILED' | 'SKIPPED' | string;
  recipient: string;
  subject?: string | null;
  templateName?: string | null;
  attachmentName?: string | null;
  errorMessage?: string | null;
  attemptedAt: string;
  sentAt?: string | null;
  resentFromEmailLogId?: string | null;
}
