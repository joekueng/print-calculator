import { Routes } from '@angular/router';
import { adminAuthGuard } from './guards/admin-auth.guard';

export const ADMIN_ROUTES: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./pages/admin-login.component').then(
        (m) => m.AdminLoginComponent,
      ),
  },
  {
    path: '',
    canActivate: [adminAuthGuard],
    loadComponent: () =>
      import('./pages/admin-shell.component').then(
        (m) => m.AdminShellComponent,
      ),
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'orders',
      },
      {
        path: 'orders',
        loadComponent: () =>
          import('./pages/admin-dashboard.component').then(
            (m) => m.AdminDashboardComponent,
          ),
      },
      {
        path: 'filament-stock',
        loadComponent: () =>
          import('./pages/admin-filament-stock.component').then(
            (m) => m.AdminFilamentStockComponent,
          ),
      },
      {
        path: 'contact-requests',
        loadComponent: () =>
          import('./pages/admin-contact-requests.component').then(
            (m) => m.AdminContactRequestsComponent,
          ),
      },
      {
        path: 'sessions',
        loadComponent: () =>
          import('./pages/admin-sessions.component').then(
            (m) => m.AdminSessionsComponent,
          ),
      },
      {
        path: 'cad-invoices',
        loadComponent: () =>
          import('./pages/admin-cad-invoices.component').then(
            (m) => m.AdminCadInvoicesComponent,
          ),
      },
      {
        path: 'qr',
        loadComponent: () =>
          import('./pages/admin-qr-links.component').then(
            (m) => m.AdminQrLinksComponent,
          ),
      },
      {
        path: 'home-media',
        pathMatch: 'full',
        redirectTo: 'media',
      },
      {
        path: 'media',
        loadComponent: () =>
          import('./pages/admin-home-media.component').then(
            (m) => m.AdminHomeMediaComponent,
          ),
      },
      {
        path: 'home-projects',
        loadComponent: () =>
          import('./pages/admin-home-projects.component').then(
            (m) => m.AdminHomeProjectsComponent,
          ),
      },
      {
        path: 'shop',
        loadComponent: () =>
          import('./pages/admin-shop.component').then(
            (m) => m.AdminShopComponent,
          ),
      },
      {
        path: 'linkedin',
        loadComponent: () =>
          import('./pages/admin-linkedin.component').then(
            (m) => m.AdminLinkedInComponent,
          ),
      },
    ],
  },
];
