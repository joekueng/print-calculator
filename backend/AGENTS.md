# Backend Guide for Coding Agents

## Orientation

The backend is a Java 21 / Spring Boot 3.4 REST application. The entry point is `BackendApplication.java`. Packages below `com.printcalculator` follow these responsibilities:

- `controller/` — public HTTP endpoints; `controller/admin/` is the authenticated administrative API.
- `service/` — domain logic. Domain subpackages include `admin`, `email`, `home`, `order`, `payment`, `qr`, `request`, `media`, and `storage`.
- `entity/`, `repository/`, `dto/`, and `model/` — persistence, data access, API contracts, and non-persistent domain values.
- `config/` and `security/` — Spring configuration, CORS/origin policy, admin sessions, CSRF, and login throttling.
- `event/` and `event/listener/` — domain-event-driven side effects such as email and QR geo-enrichment.
- `exception/` — central error mapping and domain exceptions.

Application configuration is under `src/main/resources/`; the current persistence policy is Hibernate `ddl-auto=update` in `application.properties`, and `db.sql` is repository-level database support material. OrcaSlicer profiles are under `backend/profiles/`. Local runtime storage directories are operational data, not source code.

## Implementation conventions

- Keep controllers thin: validate (`@Valid`), delegate to a service, and return DTOs. Do not expose JPA entities directly.
- Put business decisions, transaction boundaries, state transitions, and integration orchestration in services.
- Add a repository only for persistence queries that cannot be expressed through an existing one. Use clear method names and avoid leaking HTTP concerns into repositories.
- When adding or changing persistent data, assess compatibility with the current Hibernate schema-update deployment policy and any required data migration. Update the entity, DTOs, mapping, service behavior, and tests in the same change.
- Use constructor injection and existing project patterns. Do not introduce a second dependency-injection style.
- Return safe, minimal public data. Keep administrative and public contracts separate, and never treat an identifier alone as authorization.

## Domain dependencies to preserve

- Quote calculations combine model inspection, Orca profile resolution, slicing/G-code parsing, print statistics, and pricing policy. Changes require special care around `SlicerService`, `QuoteCalculator`, `QuoteSessionTotalsService`, and related entities.
- Order/payment changes can emit events consumed by email and invoice flows. Trace the relevant `event` and `event/listener` classes before changing an order status or payment state.
- Uploads and media must retain file validation, antivirus/storage handling, and access boundaries between `original`, `public`, and `private` assets.
- QR location is inferred from backend-observed IP data; proxy-header trust and GeoLite configuration are security-sensitive.
- SMTP, TWINT, QR-bill, and OpenAI translation integrations must be configuration-driven. Never log credentials or full sensitive request data.

## Testing and checks

Use the Gradle wrapper from `backend/`:

```bash
./gradlew test
./gradlew compileJava
```

Add or update focused unit tests for service logic and controller tests for API validation, authorization, and response contracts. Prefer test fixtures and H2-compatible persistence tests already used by the project. Run a relevant integration flow when changing slicing, uploads, payment state, or the persistent schema.

## Configuration safety

- Do not commit secrets or replace environment-based configuration with hard-coded values.
- Do not alter production defaults, proxy trust settings, security filters, or file-size limits without understanding their deployment impact.
- Keep generated files, temporary slicing outputs, uploaded files, and local storage out of source changes.
