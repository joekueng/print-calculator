# Gemini Project Context

This file provides Gemini and other coding agents with concise context about the project structure and domain logic.

## Project overview

**Name:** Print Calculator
**Purpose:** A complete 3D-printing platform with a real-slicing quote calculator, e-commerce shop, QR-link tracking, and showcase-project management.

**Stack:**

- **Backend:** Java 21, Spring Boot 3.4, PostgreSQL, JPA/Hibernate.
- **Frontend:** Angular 19 (TypeScript), Angular Material, and Three.js for 3D visualisation.

## Architecture

### Backend (`/backend`)

The backend is divided into functional domains:

1. **Slicing and quotes** (`service/SlicerService.java`, `QuoteCalculator`)
   - Headless **OrcaSlicer** creates real G-code for accurate time and material estimates.
   - Pricing uses tiered policies, energy cost, markup, and fixed fees.
   - `GCodeParser` extracts time and weight metadata from generated G-code comments.
   - 3MF/STL conversion and inspection use LWJGL/Assimp.
2. **Shop** (`PublicShopController`, shop/order services)
   - Product catalogue with variants, hierarchical categories, cookie/database cart handling, checkout, and fulfilment.
   - Dynamic sitemap generation is handled by `SitemapController`.
3. **QR tracking and analytics** (`service/qr`)
   - QR scans collect analytics and can be enriched with GeoLite2 IP geolocation.
4. **Media management** (`service/media`)
   - Images and videos are inspected, processed through FFmpeg, and served as optimised variants.
5. **Admin localisation** (`service/admin`)
   - OpenAI-assisted translation supports reviewed shop-product translations and generic localised text for media and home projects.
6. **Payments and invoicing** (`service/payment`)
   - Supports TWINT, Swiss QR bills, and PDF invoices.
7. **Security and infrastructure**
   - Admin authentication uses sessions, CSRF protection, and login throttling.
   - ClamAV scans uploads.
   - Filesystem storage is separated for quotes, requests, shop assets, and media.

### Frontend (`/frontend`)

The Angular standalone application is organised as:

- **Features:** calculator, shop, admin, checkout, contact, home, about, materials, legal, and order flows.
- **Shared:** reusable UI such as `stl-viewer` (Three.js), `app-dropzone`, and `brand-animation-logo`.
- **Core:** request-origin/admin-auth interceptors, SEO, and ngx-translate internationalisation services.

### Documentation and DevOps

- Mermaid architecture diagrams are in `docs/uml/`; English versions are in `docs/uml/en/`.
- Gitea workflow definitions are in `.gitea/workflows/`.
- Deployment scripts and environment templates are in `deploy/`.

## Key concepts

- **Real slicing:** Generates estimates from actual G-code rather than volume-only approximations.
- **Database-driven pricing:** Uses entities such as `PricingPolicy`, `PrinterMachine`, and `FilamentVariant`.
- **Geo-enriched analytics:** Asynchronous events enrich QR scans with geographic data.
- **Automated notifications:** Domain events trigger email for orders and custom quote requests.

## Development notes

- **Backend:** Requires JDK 21. `./gradlew bootRun` starts with the `local` profile by default.
- **Database:** PostgreSQL. The current persistence configuration uses Hibernate `ddl-auto=update`; assess production compatibility and data-migration needs for schema changes.
- **Frontend:** Requires Node.js 22. Run `npm start`.
- **External dependencies:** OrcaSlicer must be on `PATH` or configured in `application.properties`; FFmpeg processes media; ClamAV is required for production upload safety.

## Agent rules

- Angular components must use separate HTML (`templateUrl`), SCSS (`styleUrl`), and TypeScript files.
- Follow controller → service → repository conventions in Spring Boot and use constructor injection patterns already present in the codebase.
- Validate DTO input with `@Valid` and Bean Validation constraints.
- When asked to commit, keep commits small and focused by module.
- Read the root, backend, or frontend `AGENTS.md` for complete, directory-specific instructions before making changes.
