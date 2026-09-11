# Print Calculator — Repository Guide for Coding Agents

## Purpose and architecture

Print Calculator is a full-stack platform for quoting and selling 3D printing services and products. Its main domains are:

- **Quoting and slicing:** uploaded models are inspected and sliced with headless OrcaSlicer to calculate material use, print duration, and price.
- **Shop and orders:** public catalogue, cart, checkout, payments, fulfilment, and customer files.
- **Operations:** printers, materials, pricing policies, inventory, media, home projects, and translations are managed in the admin area.
- **QR tracking:** QR links record scans and may enrich them with GeoLite2 IP geolocation.
- **Swiss payments and documents:** TWINT, Swiss QR bills, invoices, and transactional email.

The repository has two deployable applications:

| Area | Location | Stack | Responsibility |
| --- | --- | --- | --- |
| Backend | `backend/` | Java 21, Spring Boot 3.4, JPA/Hibernate, PostgreSQL | REST API, domain logic, slicing, storage, payments, email |
| Frontend | `frontend/` | Angular 19 standalone, Angular Material, Three.js, ngx-translate | Public site, calculator, checkout, and admin UI |

Read the nearest `AGENTS.md` before editing. The backend and frontend guides contain the module-specific conventions and verification commands.

## Repository map

- `backend/src/main/java/com/printcalculator/` — application code, organized into `controller`, `service`, `repository`, `entity`, `dto`, `security`, `config`, `event`, and `exception`.
- `backend/src/main/resources/application.properties` — application and persistence configuration. The current schema policy is Hibernate `ddl-auto=update`; coordinate schema changes carefully with production deployment and `db.sql` where applicable.
- `backend/profiles/` — OrcaSlicer profiles and printer data. Treat profile changes as pricing-sensitive.
- `frontend/src/app/` — Angular `core`, `shared`, and feature folders.
- `frontend/src/assets/i18n/` — synchronized translation catalogs: `de.json`, `en.json`, `fr.json`, and `it.json`.
- `deploy/`, `docker-compose.yml`, `docker-compose.deploy.yml` — runtime and deployment configuration.
- `docs/uml/en/` — English architecture diagrams; matching source diagrams are under `docs/uml/`.
- `scripts/` — repository-level local checks and diagnostic scripts.

## Cross-cutting rules

- Preserve the existing controller → service → repository flow. Controllers should validate and map HTTP concerns; services own business rules and transactions.
- Use DTOs at API boundaries. Apply `@Valid` to request bodies and Bean Validation constraints to DTO fields.
- Keep schema changes, entity mappings, repositories, services, DTOs, API contracts, and frontend models aligned. The project currently uses Hibernate `ddl-auto=update`, so assess deployment compatibility and data migration needs before changing persistent data.
- Keep public and admin APIs intentionally separate. Admin endpoints use the established session and CSRF protections; do not weaken them or expose secrets/PII in public responses.
- Do not add secrets, real credentials, private storage files, or generated build artifacts to version control.
- Preserve user changes already present in the worktree. Keep commits small and module-scoped, for example `feat(shop): add product variants`.

## High-risk areas

- **Quote/slicer changes:** inspect `SlicerService`, `QuoteCalculator`, pricing-policy entities/services, and quote-session totals together. Verify results using relevant tests; slicing is CPU- and I/O-intensive.
- **Uploads and storage:** retain validation, antivirus, ownership/access checks, and the original/public/private storage distinction.
- **Payments, orders, and email:** treat status transitions as domain events. Check event listeners, invoice generation, and notification behavior when changing order states.
- **i18n:** every new user-visible frontend string needs every locale. Run `npm run check:i18n` after changing translation keys.
- **UI:** reuse shared controls and semantic UI primitives; `frontend/AGENTS.md` is mandatory for frontend changes.

## Common verification

Run the narrowest relevant checks first, then broader checks when the change crosses a boundary:

```bash
cd backend && ./gradlew test
cd frontend && npx tsc -p tsconfig.app.json --noEmit
cd frontend && npm run check:i18n && npm run check:ui-reuse
```

For externally visible behavior, also exercise the affected API or UI flow. Do not run expensive slicing or deployment operations unless the change needs them.
