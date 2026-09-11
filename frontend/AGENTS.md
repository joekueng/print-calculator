# Frontend Guide for Coding Agents

## Application map

The Angular 19 application uses standalone components and a Core / Shared / Features structure:

- `src/app/core/` — singleton services, interceptors, i18n, layout, route concerns, and global utilities.
- `src/app/shared/` — reusable presentational components, directives, and utilities. Keep these free of feature-specific business logic.
- `src/app/features/` — routed product areas: `calculator`, `shop`, `checkout`, `order`, `contact`, `home`, `about`, `materials`, `legal`, and `admin`.
- `src/styles/` — design tokens, theme, shared UI primitives, patterns, and admin styles.
- `src/assets/i18n/` — the synchronized `de`, `en`, `fr`, and `it` locale catalogs.

Routes start in `src/app/app.routes.ts`. Keep feature code within its feature folder; use `core` only for true application-wide concerns.

## UI source of truth

This project already has a shared UI layer. Do not create parallel form controls, button styles, or an admin-only fallback design system.

Use these first:

- Shared Angular controls in `src/app/shared/components/`: `app-input`, `app-select`, `app-textarea`, `app-checkbox`, `app-button`, and `app-card`.
- Global primitives in `src/styles/_ui.scss`: `ui-form-group`, `ui-form-label`, `ui-form-control`, `ui-form-error`, `ui-button`, `ui-carousel-indicators`, `ui-carousel-dot`, `ui-checkbox`, `ui-banner`, `ui-subpanel`, `ui-data-table`, `ui-table-wrap`, `ui-language-toolbar`, and `ui-file-picker`.
- Admin pages: the existing `section-card`, `section-header`, and shared `ui-*` primitives.

Apply this order:

1. Reuse a shared component when its behavior and semantics match.
2. Reuse a `ui-*` primitive when only markup/layout differs.
3. Add a shared component only for behavior that will be reused in multiple places.
4. Keep feature SCSS for layout or a genuinely page-specific treatment.

Component HTML, SCSS, and TypeScript must remain in separate files. Use explicit TypeScript types; do not introduce `any`.

## Styling rules

- Prefer semantic tokens and existing typography classes (`ui-eyebrow`, `ui-hero-display`, `ui-section-display-title`, `ui-section-display-subtitle`, `ui-copy-lead`, `ui-copy-subtitle`) over one-off colors and font sizes.
- Use the shared carousel indicator classes and `--ui-carousel-duration`; do not create feature-local dot/progress variants.
- Do not use `::ng-deep`.
- Do not add broad fallback selectors for raw `input`, `select`, `textarea`, or `button` in admin pages.
- Native controls are acceptable only when inherently special-purpose, such as hidden file inputs with `ui-file-picker`, rich-text toolbars, navigation triggers, and custom expand/collapse controls.
- Before creating a shared control, inspect the current control API. Extend it for a small generic capability instead of creating `app-admin-input`, `app-inline-button`, or similar duplicates.

## Data, API, and i18n rules

- Put API access in the relevant feature service; do not issue ad-hoc HTTP requests from presentational components.
- Update strongly typed API models alongside backend contract changes. Keep public and admin behavior explicit.
- Every new visible string requires matching keys and English translations in all locale catalogs. Do not use a translation fallback as a substitute for a missing locale key.
- Use Angular forms and existing validation/error-display patterns. Do not bypass server-side validation.

## Verification

From `frontend/`, run the checks relevant to the change:

```bash
npx tsc -p tsconfig.app.json --noEmit
npm run check:i18n
npm run check:ui-reuse
npm test
```

When touching UI or admin pages, also inspect for prohibited patterns:

```bash
rg -n "::ng-deep" src --glob '*.scss'
rg -n "<(input|select|textarea|button)\\b" src/app/features/admin/pages --glob '*.html'
```

Raw controls found by the second command are allowed only for the special-purpose exceptions above. Verify responsive behavior for user-facing UI changes.
