# Print Calculator Frontend

This is a modern Angular standalone application organised around Core, Shared, and Feature areas, with design tokens for easy theming.

## Project Structure

- **Core**: Singleton services, global layout components (Navbar, Footer), guards.
- **Shared**: Reusable dumb UI components (Buttons, Cards, Inputs). No business logic.
- **Features**: Routed product areas (Calculator, Shop, About, Admin, Checkout, and more). Each contains its own pages, components, and services.
- **Styles**: Design tokens and theming layer.

## Getting Started

1. **Install Dependencies**:
   ```bash
   npm install
   ```

2. **Run Development Server**:
   ```bash
   ng serve
   ```
   Navigate to `http://localhost:4200/`.

## Theming

The application uses CSS Variables defined in `src/styles/tokens.scss` and mapped in `src/styles/theme.scss`.

- **Change Colors**: Edit `src/styles/tokens.scss`.
- **Create New Theme**:
  1. Duplicate `src/styles/theme.scss` (e.g., `theme-dark.scss`).
  2. Override the semantic variables (e.g., `--color-bg`, `--color-text`).
  3. Load the new theme file or switch classes on the body tag.

## Adding a New Feature

1. **Create Directory**: `src/app/features/my-feature`.
2. **Create Routes**: Create `my-feature.routes.ts` exporting a `Routes` array.
3. **Register Route**: Add to `src/app/app.routes.ts` using lazy loading:
   ```typescript
   {
     path: 'my-feature',
     loadChildren: () => import('./features/my-feature/my-feature.routes').then(m => m.MY_FEATURE_ROUTES)
   }
   ```

## Internationalization (i18n)

Translations are stored in `src/assets/i18n/`.

- `it.json` (Italian, the default and fallback locale)
- `en.json` (English)
- `de.json` (German)
- `fr.json` (French)

To add a language, create the JSON file, update the supported-language types and `LanguageService` in `src/app/core/services/language.service.ts`, and update the static translation loader. Run `npm run check:i18n` before committing.
