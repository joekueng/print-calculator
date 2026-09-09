# Print Calculator (OrcaSlicer Edition)

A full-stack application (Angular + Spring Boot) for calculating accurate 3D-printing quotes with **OrcaSlicer** running headlessly.

## Features

* **Real slicing:** Uses the OrcaSlicer engine to estimate print time and material as accurately as possible.
* **Database-driven quoting:** Calculates prices from configurable database policies for material cost, tiered machine depreciation, electricity, and markup.
* **3D preview:** Displays uploaded STL files with Three.js.
* **Multiple profiles:** Supports different printers, materials, and process profiles.

## Technology stack

- **Backend**: Java 21, Spring Boot 3.4, PostgreSQL.
- **Frontend**: Angular 19, Angular Material, Three.js.
- **Slicer**: OrcaSlicer (invocato via CLI).

## Prerequisites

* **Java 21** installed.
* **Node.js 22** and **npm** installed.
* A running **PostgreSQL** instance.
* **OrcaSlicer** installed on the system.
* **FFmpeg** installed on the system or included in the backend Docker image.

## Quick start

### 1. Database
Create a PostgreSQL database named `printcalc`. The project manages the schema through its JPA/SQL configuration.

### 2. Backend
Configure the OrcaSlicer path in `backend/src/main/resources/application.properties` or with the `SLICER_PATH` environment variable. The public media service also supports:

- `MEDIA_STORAGE_ROOT` — the backend `storage_media` root (`original/`, `public/`, `private/`)
- `SHOP_STORAGE_ROOT` — the backend `storage_shop` root for shop product models
- `MEDIA_FFMPEG_PATH` — the `ffmpeg` binary (Docker deployment default: `/usr/local/bin/ffmpeg-media`)
- `MEDIA_UPLOAD_MAX_FILE_SIZE_BYTES` — the maximum image-asset size

```bash
cd backend
./gradlew bootRun
```

### 3. Frontend
```bash
cd frontend
npm install
npm start
```

Open [http://localhost:4200](http://localhost:4200).

## Price configuration

Prices are managed in database tables rather than fixed environment variables:
- `pricing_policy`: Defines markup, fixed fees, and electricity cost.
- `pricing_policy_machine_hour_tier`: Defines machine hourly costs by print-duration tier.
- `printer_machine`: Printer catalogue and energy consumption.
- `filament_material_type` / `filament_variant`: Material price list.

## Project structure

* `/backend`: Spring Boot API.
* `/frontend`: Angular application.
* `/backend/profiles`: OrcaSlicer configuration files.
* `/storage_media`: Original files and public/private media variants on the filesystem.
* `/storage_shop`: Shop product models and files.

## Public media

The backend always stores the original in `storage_media/original/` and pre-generates public variants in `storage_media/public/`. `storage_media/private/` is reserved for non-public assets.

In Docker deployments, the expected volumes are `/mnt/cache/appdata/print-calculator/${ENV}/storage_media:/app/storage_media` and `/mnt/cache/appdata/print-calculator/${ENV}/storage_shop:/app/storage_shop`.

Nginx must serve public files directly rather than proxying them through the backend. Expected configuration:

```nginx
location /media/ {
    alias /mnt/cache/appdata/print-calculator/${ENV}/storage_media/public/;
}
```

Initial frontend usage keys:

- `HOME_SECTION / shop-gallery`
- `HOME_SECTION / founders-gallery`
- `HOME_SECTION / capability-prototyping`
- `HOME_SECTION / capability-custom-parts`
- `HOME_SECTION / capability-small-series`
- `HOME_SECTION / capability-cad`
- `HOME_PROJECT / <home-project-slug>`
- `ABOUT_MEMBER / joe`
- `ABOUT_MEMBER / matteo`
- Reserved for future extensions: `SHOP_PRODUCT`, `SHOP_CATEGORY`, `SHOP_GALLERY`

Operationally:

- Upload files through the backend media-admin endpoint.
- Associate each asset with `POST /api/admin/media/usages`.
- For `ABOUT_MEMBER`, set `isPrimary=true` on the member's primary photo.
- Home and About load assets from `GET /api/public/media/usages?usageType=...&usageKey=...`.
- The frontend uses `<picture>`, prefers AVIF/WEBP with JPEG fallback, and does not use the original asset.
- The frontend back office manages home media from `admin/home-media`.

## QR tracking and geolocation

QR tracking infers a location only from the public IP observed by the backend through GeoLite2 City. It does not use GPS: city and region are estimates, while the country is generally more reliable.

For reliable locations behind a reverse proxy, Nginx must forward the real IP and the backend must trust only the proxy network:

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:${BACKEND_PORT};
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Forwarded "";
}
```

Expected runtime configuration:

- `APP_QR_TRUST_PROXY_HEADERS=true`
- `APP_QR_TRUSTED_PROXY_NETWORKS=172.16.0.0/12` if the backend receives requests from the Docker bridge, or the exact CIDR shown in `remoteAddrNormalized` logs
- `APP_QR_GEO_ENABLED=true`
- `APP_QR_GEO_DB_PATH=/app/cache/geoip/GeoLite2-City.mmdb` with the file mounted and readable in the container

`APP_QR_DEBUG_LOGGING=true` helps diagnose `remoteAddr`, proxy headers, and the resolved IP, but logs IP addresses. Use it only while debugging.

## LinkedIn company posts

The backend refreshes the latest company posts every day at 04:00 Europe/Zurich and exposes its cached result at `GET /api/public/linkedin/posts`. An authenticated administrator can inspect the synchronization status and start it immediately from the LinkedIn back-office page. The About page rotates the cached posts every 30 seconds and falls back to two localized entries when LinkedIn is unavailable or not configured.

Automatic synchronization requires a LinkedIn Developer application approved for the Community Management API, the `r_organization_social` permission, and an authenticated administrator of the company page. Configure these runtime variables without committing their values:

- `LINKEDIN_ACCESS_TOKEN` — LinkedIn OAuth access token
- `LINKEDIN_ORGANIZATION_URN` — company identifier such as `urn:li:organization:123456`
- `LINKEDIN_SYNC_CRON` — Spring cron expression, default `0 0 4 * * *`
- `LINKEDIN_SYNC_ZONE` — cron time zone, default `Europe/Zurich`
- `LINKEDIN_POST_COUNT` — cached post count, default `5`
- `LINKEDIN_API_VERSION` — version header, default `202608`

## License

This project is proprietary software. All rights reserved.

No commercial use, modification, redistribution, sublicensing, hosting, or
derivative works are permitted without prior written permission.
See [LICENSE](./LICENSE).

## Troubleshooting

### OrcaSlicer path
Ensure that `slicer.path` points to the correct binary. On macOS it is usually `/Applications/OrcaSlicer.app/Contents/MacOS/OrcaSlicer`. On Linux it is the path to the AppImage, extracted or otherwise.

### FFmpeg and public media
Check that `MEDIA_FFMPEG_PATH` points to an `ffmpeg` build with JPEG, WebP, and AVIF support (AVIF encoder and muxer). The backend container default is `/usr/local/bin/ffmpeg-media`; use `/usr/bin/ffmpeg` if it is compatible, otherwise install a static AVIF-capable fallback. If media URLs returned by admin APIs are unreachable, verify that `APP_FRONTEND_BASE_URL` uses the correct domain, Nginx exposes `location /media/`, and the `storage_media` volume is mounted correctly.

### Database connection
Check the credentials in `application.properties`. With Docker, you can supply `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` as environment variables.

### Deployment and OpenAI translations
For Gitea deployment, the OpenAI key must be in the `OPENAI_API_KEY` secret. The pipeline adds it to the environment `.env` file during deployment and the backend container receives it as a runtime variable. `deploy/envs/*.env` remains for `dev`/`int`/`prod`-specific values.
