create table printer_machine
(
    printer_machine_id   bigserial primary key,
    printer_display_name text          not null unique,

    build_volume_x_mm    integer       not null check (build_volume_x_mm > 0),
    build_volume_y_mm    integer       not null check (build_volume_y_mm > 0),
    build_volume_z_mm    integer       not null check (build_volume_z_mm > 0),

    power_watts          integer       not null check (power_watts > 0),

    fleet_weight         numeric(6, 3) not null default 1.000,

    is_active            boolean       not null default true,
    created_at           timestamptz   not null default now()
);

create view printer_fleet_current as
select case
           when sum(fleet_weight) = 0 then null
           else round(sum(power_watts * fleet_weight) / sum(fleet_weight))::integer
           end                as weighted_average_power_watts,
       max(build_volume_x_mm) as fleet_max_build_x_mm,
       max(build_volume_y_mm) as fleet_max_build_y_mm,
       max(build_volume_z_mm) as fleet_max_build_z_mm
from printer_machine
where is_active = true;



create table filament_material_type
(
    filament_material_type_id bigserial primary key,
    material_code             text    not null unique,        -- PLA, PETG, TPU, ASA...
    is_flexible               boolean not null default false, -- sì/no
    is_technical              boolean not null default false, -- sì/no
    technical_type_label      text                            -- es: "alta temperatura", "rinforzato", ecc.
);

create table filament_variant
(
    filament_variant_id       bigserial primary key,
    filament_material_type_id bigint         not null references filament_material_type (filament_material_type_id),

    variant_display_name      text           not null, -- es: "PLA Nero Opaco BrandX"
    color_name                text           not null, -- Nero, Bianco, ecc.
    color_label_it            text,
    color_label_en            text,
    color_label_de            text,
    color_label_fr            text,
    color_hex                 text,
    finish_type               text           not null default 'GLOSSY'
        check (finish_type in ('GLOSSY', 'MATTE', 'MARBLE', 'SILK', 'TRANSLUCENT', 'SPECIAL')),
    brand                     text,
    is_matte                  boolean        not null default false,
    is_special                boolean        not null default false,

    cost_chf_per_kg           numeric(10, 2) not null,

    -- Stock espresso in rotoli anche frazionati
    stock_spools              numeric(6, 3)  not null default 0.000,
    spool_net_kg              numeric(6, 3)  not null default 1.000,

    is_active                 boolean        not null default true,
    created_at                timestamptz    not null default now(),

    unique (filament_material_type_id, variant_display_name)
);

create view filament_variant_stock_kg as
select filament_variant_id,
       stock_spools,
       spool_net_kg,
       (stock_spools * spool_net_kg) as stock_kg
from filament_variant;

alter table filament_variant
    add column if not exists color_label_it text,
    add column if not exists color_label_en text,
    add column if not exists color_label_de text,
    add column if not exists color_label_fr text;

update filament_variant
set color_label_it = coalesce(nullif(btrim(color_label_it), ''), color_name),
    color_label_en = coalesce(nullif(btrim(color_label_en), ''), color_name),
    color_label_de = coalesce(nullif(btrim(color_label_de), ''), color_name),
    color_label_fr = coalesce(nullif(btrim(color_label_fr), ''), color_name)
where nullif(btrim(color_label_it), '') is null
   or nullif(btrim(color_label_en), '') is null
   or nullif(btrim(color_label_de), '') is null
   or nullif(btrim(color_label_fr), '') is null;

create table printer_machine_profile
(
    printer_machine_profile_id bigserial primary key,
    printer_machine_id         bigint         not null references printer_machine (printer_machine_id) on delete cascade,
    nozzle_diameter_mm         numeric(4, 2)  not null check (nozzle_diameter_mm > 0),
    orca_machine_profile_name  text           not null,
    is_default                 boolean        not null default false,
    is_active                  boolean        not null default true,
    unique (printer_machine_id, nozzle_diameter_mm)
);

create table material_orca_profile_map
(
    material_orca_profile_map_id bigserial primary key,
    printer_machine_profile_id   bigint  not null references printer_machine_profile (printer_machine_profile_id) on delete cascade,
    filament_material_type_id    bigint  not null references filament_material_type (filament_material_type_id),
    orca_filament_profile_name   text    not null,
    is_active                    boolean not null default true,
    unique (printer_machine_profile_id, filament_material_type_id)
);

create table filament_variant_orca_override
(
    filament_variant_orca_override_id bigserial primary key,
    filament_variant_id               bigint  not null references filament_variant (filament_variant_id) on delete cascade,
    printer_machine_profile_id        bigint  not null references printer_machine_profile (printer_machine_profile_id) on delete cascade,
    orca_filament_profile_name        text    not null,
    is_active                         boolean not null default true,
    unique (filament_variant_id, printer_machine_profile_id)
);



create table pricing_policy
(
    pricing_policy_id            bigserial primary key,

    policy_name                  text           not null,              -- es: "2026 Q1", "Default", ecc.

    -- validità temporale (consiglio: valid_to esclusiva)
    valid_from                   timestamptz    not null,
    valid_to                     timestamptz,

    electricity_cost_chf_per_kwh numeric(10, 6) not null,
    markup_percent               numeric(6, 3)  not null default 20.000,

    fixed_job_fee_chf            numeric(10, 2) not null default 0.00, -- "costo fisso"
    nozzle_change_base_fee_chf   numeric(10, 2) not null default 0.00, -- base cambio ugello, se vuoi
    cad_cost_chf_per_hour        numeric(10, 2) not null default 0.00,

    is_active                    boolean        not null default true,
    created_at                   timestamptz    not null default now()
);

create table pricing_policy_machine_hour_tier
(
    pricing_policy_machine_hour_tier_id bigserial primary key,
    pricing_policy_id                   bigint         not null references pricing_policy (pricing_policy_id),

    tier_start_hours                    numeric(10, 2) not null,
    tier_end_hours                      numeric(10, 2), -- null = infinito
    machine_cost_chf_per_hour           numeric(10, 2) not null,

    constraint chk_tier_start_non_negative check (tier_start_hours >= 0),
    constraint chk_tier_end_gt_start check (tier_end_hours is null or tier_end_hours > tier_start_hours)
);

create index idx_pricing_policy_validity
    on pricing_policy (valid_from, valid_to);

create index idx_pricing_tier_lookup
    on pricing_policy_machine_hour_tier (pricing_policy_id, tier_start_hours);


create table nozzle_option
(
    nozzle_option_id            bigserial primary key,
    nozzle_diameter_mm          numeric(4, 2)  not null unique, -- 0.4, 0.6, 0.8...

    owned_quantity              integer        not null default 0 check (owned_quantity >= 0),

    -- extra costo specifico oltre ad eventuale base fee della pricing_policy
    extra_nozzle_change_fee_chf numeric(10, 2) not null default 0.00,

    is_active                   boolean        not null default true,
    created_at                  timestamptz    not null default now()
);


create table layer_height_option
(
    layer_height_option_id bigserial primary key,
    layer_height_mm        numeric(5, 3) not null unique, -- 0.12, 0.20, 0.28...

    -- opzionale: moltiplicatore costo/tempo (es: 0.12 costa di più)
    time_multiplier        numeric(6, 3) not null default 1.000,

    is_active              boolean       not null default true
);

create table layer_height_profile
(
    layer_height_profile_id bigserial primary key,
    profile_name            text          not null unique, -- "Standard", "Fine", ecc.

    min_layer_height_mm     numeric(5, 3) not null,
    max_layer_height_mm     numeric(5, 3) not null,
    default_layer_height_mm numeric(5, 3) not null,

    time_multiplier         numeric(6, 3) not null default 1.000,

    constraint chk_layer_range check (max_layer_height_mm >= min_layer_height_mm)
);

create table if not exists nozzle_layer_height_option
(
    nozzle_layer_height_option_id bigserial primary key,
    nozzle_diameter_mm            numeric(4, 2) not null,
    layer_height_mm               numeric(5, 3) not null,
    is_active                     boolean       not null default true,
    constraint ux_nozzle_layer_height_option_nozzle_layer
        unique (nozzle_diameter_mm, layer_height_mm)
);


begin;

set timezone = 'Europe/Zurich';

-- =========================================================
-- 0) (Solo se non esiste) tabella infill_pattern + seed
-- =========================================================
-- Se la tabella esiste già, commenta questo blocco.
create table if not exists infill_pattern
(
    infill_pattern_id bigserial primary key,
    pattern_code      text    not null unique, -- es: grid, gyroid
    display_name      text    not null,
    is_active         boolean not null default true
);

insert into infill_pattern (pattern_code, display_name, is_active)
values ('grid', 'Grid', true),
       ('gyroid', 'Gyroid', true)
on conflict (pattern_code) do update
    set display_name = excluded.display_name,
        is_active    = excluded.is_active;


-- =========================================================
-- 1) Pricing policy (valori ESATTI da Excel)
-- Valid from: 2026-01-01, valid_to: NULL
-- =========================================================
insert into pricing_policy (policy_name,
                            valid_from,
                            valid_to,
                            electricity_cost_chf_per_kwh,
                            markup_percent,
                            fixed_job_fee_chf,
                            nozzle_change_base_fee_chf,
                            cad_cost_chf_per_hour,
                            is_active)
values ('Excel Tariffe 2026-01-01',
        '2026-01-01 00:00:00+01'::timestamptz,
        null,
        0.156, -- Costo elettricità CHF/kWh (Excel)
        0.000, -- Markup non specificato -> 0 (puoi cambiarlo dopo)
        1.00, -- Costo fisso macchina CHF (Excel)
        0.00, -- Base cambio ugello: non specificato -> 0
        25.00, -- Tariffa CAD CHF/h (Excel)
        true)
on conflict do nothing;

-- scaglioni tariffa stampa (Excel)
insert into pricing_policy_machine_hour_tier (pricing_policy_id,
                                              tier_start_hours,
                                              tier_end_hours,
                                              machine_cost_chf_per_hour)
select p.pricing_policy_id,
       tiers.tier_start_hours,
       tiers.tier_end_hours,
       tiers.machine_cost_chf_per_hour
from pricing_policy p
         cross join (values (0.00::numeric, 10.00::numeric, 2.00::numeric),  -- 0–10 h
                            (10.00::numeric, 20.00::numeric, 1.40::numeric), -- 10–20 h
                            (20.00::numeric, null::numeric, 0.50::numeric) -- >20 h
) as tiers(tier_start_hours, tier_end_hours, machine_cost_chf_per_hour)
where p.policy_name = 'Excel Tariffe 2026-01-01'
on conflict do nothing;


-- =========================================================
-- 2) Stampante: BambuLab A1
-- =========================================================
insert into printer_machine (printer_display_name,
                             build_volume_x_mm,
                             build_volume_y_mm,
                             build_volume_z_mm,
                             power_watts,
                             fleet_weight,
                             is_active)
values ('BambuLab A1',
        256,
        256,
        256,
        150, -- hai detto "150, 140": qui ho messo 150
        1.000,
        true)
on conflict (printer_display_name) do update
    set build_volume_x_mm = excluded.build_volume_x_mm,
        build_volume_y_mm = excluded.build_volume_y_mm,
        build_volume_z_mm = excluded.build_volume_z_mm,
        power_watts       = excluded.power_watts,
        fleet_weight      = excluded.fleet_weight,
        is_active         = excluded.is_active;

-- =========================================================
-- 2b) Stampante: BambuLab P2S
-- Dati di build dal profilo Orca locale; potenza assunta a 350W
-- finche' non viene confermata da una scheda tecnica ufficiale.
-- =========================================================
insert into printer_machine (printer_display_name,
                             build_volume_x_mm,
                             build_volume_y_mm,
                             build_volume_z_mm,
                             power_watts,
                             fleet_weight,
                             is_active)
values ('BambuLab P2S',
        256,
        256,
        256,
        350,
        1.000,
        true)
on conflict (printer_display_name) do update
    set build_volume_x_mm = excluded.build_volume_x_mm,
        build_volume_y_mm = excluded.build_volume_y_mm,
        build_volume_z_mm = excluded.build_volume_z_mm,
        power_watts       = excluded.power_watts,
        fleet_weight      = excluded.fleet_weight,
        is_active         = excluded.is_active;


-- =========================================================
-- 3) Material types (da Excel) - per ora niente technical
-- =========================================================
insert into filament_material_type (material_code,
                                    is_flexible,
                                    is_technical,
                                    technical_type_label)
values ('PLA', false, false, null),
       ('PLA TOUGH', false, false, null),
       ('PETG', false, false, null),
       ('TPU', true, false, null),
       ('PC', false, true, 'engineering'),
       ('ABS', false, false, null),
       ('Nylon', false, false, null),
       ('Carbon PLA', false, false, null)
on conflict (material_code) do update
    set is_flexible          = excluded.is_flexible,
        is_technical         = excluded.is_technical,
        technical_type_label = excluded.technical_type_label;


-- =========================================================
-- 4) Filament variants (PLA colori) - costi da Excel
-- Excel: PLA = 18 CHF/kg, TPU = 42 CHF/kg (non inserito perché quantità non chiara)
-- Stock in "rotoli" (3 = 3 kg se spool_net_kg=1)
-- =========================================================

-- helper: ID PLA
with pla as (select filament_material_type_id
             from filament_material_type
             where material_code = 'PLA')
insert
into filament_variant (filament_material_type_id,
                       variant_display_name,
                       color_name,
                       color_hex,
                       finish_type,
                       brand,
                       is_matte,
                       is_special,
                       cost_chf_per_kg,
                       stock_spools,
                       spool_net_kg,
                       is_active)
select pla.filament_material_type_id,
       v.variant_display_name,
       v.color_name,
       v.color_hex,
       v.finish_type,
       null::text as brand,
       v.is_matte,
       v.is_special,
       18.00, -- PLA da Excel
       v.stock_spools,
       1.000,
       true
from pla
         cross join (values ('PLA Bianco', 'Bianco', '#F5F5F5', 'GLOSSY', false, false, 3.000::numeric),
                            ('PLA Nero', 'Nero', '#1A1A1A', 'GLOSSY', false, false, 3.000::numeric),
                            ('PLA Blu', 'Blu', '#1976D2', 'GLOSSY', false, false, 1.000::numeric),
                            ('PLA Arancione', 'Arancione', '#FFA726', 'GLOSSY', false, false, 1.000::numeric),
                            ('PLA Grigio', 'Grigio', '#BDBDBD', 'GLOSSY', false, false, 1.000::numeric),
                            ('PLA Grigio Scuro', 'Grigio scuro', '#424242', 'MATTE', true, false, 1.000::numeric),
                            ('PLA Grigio Chiaro', 'Grigio chiaro', '#D6D6D6', 'MATTE', true, false, 1.000::numeric),
                            ('PLA Viola', 'Viola', '#7B1FA2', 'GLOSSY', false, false,
                             1.000::numeric)) as v(variant_display_name, color_name, color_hex, finish_type, is_matte, is_special, stock_spools)
on conflict (filament_material_type_id, variant_display_name) do update
    set color_name      = excluded.color_name,
        color_hex       = excluded.color_hex,
        finish_type     = excluded.finish_type,
        brand           = excluded.brand,
        is_matte        = excluded.is_matte,
        is_special      = excluded.is_special,
        cost_chf_per_kg = excluded.cost_chf_per_kg,
        stock_spools    = excluded.stock_spools,
        spool_net_kg    = excluded.spool_net_kg,
        is_active       = excluded.is_active;

-- Varianti base per materiali principali del calcolatore
with mat as (select filament_material_type_id
             from filament_material_type
             where material_code = 'PLA TOUGH')
insert
into filament_variant (filament_material_type_id, variant_display_name, color_name, color_hex, finish_type, brand,
                       is_matte, is_special, cost_chf_per_kg, stock_spools, spool_net_kg, is_active)
select mat.filament_material_type_id,
       'PLA Tough Nero',
       'Nero',
       '#1A1A1A',
       'GLOSSY',
       'Bambu',
       false,
       false,
       18.00,
       1.000,
       1.000,
       true
from mat
on conflict (filament_material_type_id, variant_display_name) do update
    set color_name      = excluded.color_name,
        color_hex       = excluded.color_hex,
        finish_type     = excluded.finish_type,
        brand           = excluded.brand,
        is_matte        = excluded.is_matte,
        is_special      = excluded.is_special,
        cost_chf_per_kg = excluded.cost_chf_per_kg,
        stock_spools    = excluded.stock_spools,
        spool_net_kg    = excluded.spool_net_kg,
        is_active       = excluded.is_active;

with mat as (select filament_material_type_id
             from filament_material_type
             where material_code = 'PETG')
insert
into filament_variant (filament_material_type_id, variant_display_name, color_name, color_hex, finish_type, brand,
                       is_matte, is_special, cost_chf_per_kg, stock_spools, spool_net_kg, is_active)
select mat.filament_material_type_id,
       'PETG Nero',
       'Nero',
       '#1A1A1A',
       'GLOSSY',
       'Bambu',
       false,
       false,
       24.00,
       1.000,
       1.000,
       true
from mat
on conflict (filament_material_type_id, variant_display_name) do update
    set color_name      = excluded.color_name,
        color_hex       = excluded.color_hex,
        finish_type     = excluded.finish_type,
        brand           = excluded.brand,
        is_matte        = excluded.is_matte,
        is_special      = excluded.is_special,
        cost_chf_per_kg = excluded.cost_chf_per_kg,
        stock_spools    = excluded.stock_spools,
        spool_net_kg    = excluded.spool_net_kg,
        is_active       = excluded.is_active;

with mat as (select filament_material_type_id
             from filament_material_type
             where material_code = 'TPU')
insert
into filament_variant (filament_material_type_id, variant_display_name, color_name, color_hex, finish_type, brand,
                       is_matte, is_special, cost_chf_per_kg, stock_spools, spool_net_kg, is_active)
select mat.filament_material_type_id,
       'TPU Nero',
       'Nero',
       '#1A1A1A',
       'GLOSSY',
       'Bambu',
       false,
       false,
       42.00,
       1.000,
       1.000,
       true
from mat
on conflict (filament_material_type_id, variant_display_name) do update
    set color_name      = excluded.color_name,
        color_hex       = excluded.color_hex,
        finish_type     = excluded.finish_type,
        brand           = excluded.brand,
        is_matte        = excluded.is_matte,
        is_special      = excluded.is_special,
        cost_chf_per_kg = excluded.cost_chf_per_kg,
        stock_spools    = excluded.stock_spools,
        spool_net_kg    = excluded.spool_net_kg,
        is_active       = excluded.is_active;

with mat as (select filament_material_type_id
             from filament_material_type
             where material_code = 'PC')
insert
into filament_variant (filament_material_type_id, variant_display_name, color_name, color_hex, finish_type, brand,
                       is_matte, is_special, cost_chf_per_kg, stock_spools, spool_net_kg, is_active)
select mat.filament_material_type_id,
       'PC Naturale',
       'Naturale',
       '#D9D9D9',
       'TRANSLUCENT',
       'Generic',
       false,
       true,
       48.00,
       1.000,
       1.000,
       true
from mat
on conflict (filament_material_type_id, variant_display_name) do update
    set color_name      = excluded.color_name,
        color_hex       = excluded.color_hex,
        finish_type     = excluded.finish_type,
        brand           = excluded.brand,
        is_matte        = excluded.is_matte,
        is_special      = excluded.is_special,
        cost_chf_per_kg = excluded.cost_chf_per_kg,
        stock_spools    = excluded.stock_spools,
        spool_net_kg    = excluded.spool_net_kg,
        is_active       = excluded.is_active;


-- =========================================================
-- 5) Ugelli
-- 0.4 standard (0 extra), 0.6 con attivazione 50 CHF
-- =========================================================
insert into nozzle_option (nozzle_diameter_mm,
                           owned_quantity,
                           extra_nozzle_change_fee_chf,
                           is_active)
values (0.40, 1, 0.00, true),
       (0.60, 1, 50.00, true)
on conflict (nozzle_diameter_mm) do update
    set owned_quantity              = excluded.owned_quantity,
        extra_nozzle_change_fee_chf = excluded.extra_nozzle_change_fee_chf,
        is_active                   = excluded.is_active;

-- =========================================================
-- 5a) Layer heights per nozzle (policy globale)
-- Unione conservativa dei layer emersi dai profili Bambu usati
-- localmente, cosi' l'intersezione con i process profile non tronca
-- opzioni valide per A1/P2S.
-- =========================================================
insert into nozzle_layer_height_option (nozzle_diameter_mm,
                                        layer_height_mm,
                                        is_active)
values (0.20, 0.040, true),
       (0.20, 0.060, true),
       (0.20, 0.080, true),
       (0.20, 0.100, true),
       (0.20, 0.120, true),
       (0.20, 0.140, true),
       (0.40, 0.080, true),
       (0.40, 0.120, true),
       (0.40, 0.160, true),
       (0.40, 0.200, true),
       (0.40, 0.240, true),
       (0.40, 0.280, true),
       (0.60, 0.160, true),
       (0.60, 0.180, true),
       (0.60, 0.200, true),
       (0.60, 0.240, true),
       (0.60, 0.300, true),
       (0.60, 0.360, true),
       (0.60, 0.420, true),
       (0.80, 0.200, true),
       (0.80, 0.240, true),
       (0.80, 0.280, true),
       (0.80, 0.320, true),
       (0.80, 0.360, true),
       (0.80, 0.400, true),
       (0.80, 0.480, true),
       (0.80, 0.560, true)
on conflict (nozzle_diameter_mm, layer_height_mm) do update
    set is_active = excluded.is_active;

-- =========================================================
-- 5b) Orca machine/material mapping (data-driven)
-- =========================================================
with a1 as (select printer_machine_id
            from printer_machine
            where printer_display_name = 'BambuLab A1')
insert
into printer_machine_profile (printer_machine_id, nozzle_diameter_mm, orca_machine_profile_name, is_default, is_active)
select a1.printer_machine_id, v.nozzle_diameter_mm, v.profile_name, v.is_default, true
from a1
         cross join (values (0.40::numeric, 'Bambu Lab A1 0.4 nozzle', true),
                            (0.20::numeric, 'Bambu Lab A1 0.2 nozzle', false),
                            (0.60::numeric, 'Bambu Lab A1 0.6 nozzle', false),
                            (0.80::numeric, 'Bambu Lab A1 0.8 nozzle', false))
             as v(nozzle_diameter_mm, profile_name, is_default)
on conflict (printer_machine_id, nozzle_diameter_mm) do update
    set orca_machine_profile_name = excluded.orca_machine_profile_name,
        is_default                = excluded.is_default,
        is_active                 = excluded.is_active;

with p2s as (select printer_machine_id
             from printer_machine
             where printer_display_name = 'BambuLab P2S')
insert
into printer_machine_profile (printer_machine_id, nozzle_diameter_mm, orca_machine_profile_name, is_default, is_active)
select p2s.printer_machine_id, v.nozzle_diameter_mm, v.profile_name, v.is_default, true
from p2s
         cross join (values (0.40::numeric, 'Bambu Lab P2S 0.4 nozzle', true),
                            (0.20::numeric, 'Bambu Lab P2S 0.2 nozzle', false),
                            (0.60::numeric, 'Bambu Lab P2S 0.6 nozzle', false),
                            (0.80::numeric, 'Bambu Lab P2S 0.8 nozzle', false))
             as v(nozzle_diameter_mm, profile_name, is_default)
on conflict (printer_machine_id, nozzle_diameter_mm) do update
    set orca_machine_profile_name = excluded.orca_machine_profile_name,
        is_default                = excluded.is_default,
        is_active                 = excluded.is_active;

with p as (select printer_machine_profile_id
           from printer_machine_profile pmp
                    join printer_machine pm on pm.printer_machine_id = pmp.printer_machine_id
           where pm.printer_display_name = 'BambuLab A1'
             and pmp.nozzle_diameter_mm = 0.40::numeric),
     m as (select filament_material_type_id, material_code
           from filament_material_type
           where material_code in ('PLA', 'PLA TOUGH', 'PETG', 'TPU', 'PC'))
insert
into material_orca_profile_map (printer_machine_profile_id, filament_material_type_id, orca_filament_profile_name, is_active)
select p.printer_machine_profile_id,
       m.filament_material_type_id,
       case m.material_code
           when 'PLA' then 'Bambu PLA Basic @BBL A1'
           when 'PLA TOUGH' then 'Bambu PLA Tough @BBL A1'
           when 'PETG' then 'Bambu PETG Basic @BBL A1'
           when 'TPU' then 'Bambu TPU 95A @BBL A1'
           when 'PC' then 'Generic PC @BBL A1'
           end,
       true
from p
         cross join m
on conflict (printer_machine_profile_id, filament_material_type_id) do update
    set orca_filament_profile_name = excluded.orca_filament_profile_name,
        is_active                  = excluded.is_active;

with p as (select pmp.printer_machine_profile_id,
                  pmp.nozzle_diameter_mm
           from printer_machine_profile pmp
                    join printer_machine pm on pm.printer_machine_id = pmp.printer_machine_id
           where pm.printer_display_name = 'BambuLab P2S'
             and pmp.nozzle_diameter_mm in (0.40::numeric, 0.60::numeric, 0.80::numeric)),
     m as (select filament_material_type_id, material_code
           from filament_material_type
           where material_code in ('PLA', 'PLA TOUGH', 'PETG', 'TPU', 'PC'))
insert
into material_orca_profile_map (printer_machine_profile_id, filament_material_type_id, orca_filament_profile_name, is_active)
select p.printer_machine_profile_id,
       m.filament_material_type_id,
       case
           when m.material_code = 'PLA' and p.nozzle_diameter_mm = 0.40::numeric
               then 'Bambu PLA Basic @BBL P2S'
           when m.material_code = 'PLA' and p.nozzle_diameter_mm = 0.60::numeric
               then 'Bambu PLA Basic @BBL P2S 0.6 nozzle'
           when m.material_code = 'PLA' and p.nozzle_diameter_mm = 0.80::numeric
               then 'Bambu PLA Basic @BBL P2S 0.8 nozzle'
           when m.material_code = 'PLA TOUGH'
               then 'Bambu PLA Tough @BBL P2S'
           when m.material_code = 'PETG'
               then 'Generic PETG @BBL P2S'
           when m.material_code = 'TPU'
               then 'Bambu TPU 95A @BBL P2S'
           when m.material_code = 'PC'
               then 'Generic PC @BBL P2S'
           end,
       true
from p
         cross join m
on conflict (printer_machine_profile_id, filament_material_type_id) do update
    set orca_filament_profile_name = excluded.orca_filament_profile_name,
        is_active                  = excluded.is_active;


-- =========================================================
-- 6) Layer heights (opzioni)
-- =========================================================
insert into layer_height_option (layer_height_mm,
                                 time_multiplier,
                                 is_active)
values (0.080, 1.000, true),
       (0.120, 1.000, true),
       (0.160, 1.000, true),
       (0.200, 1.000, true),
       (0.240, 1.000, true),
       (0.280, 1.000, true)
on conflict (layer_height_mm) do update
    set time_multiplier = excluded.time_multiplier,
        is_active       = excluded.is_active;

commit;


-- Sostituisci __MULTIPLIER__ con il tuo valore (es. 1.10)
update layer_height_option
set time_multiplier = 0.1
where layer_height_mm = 0.080;


-- =========================
-- CUSTOMERS (minimo indispensabile)
-- =========================
CREATE TABLE IF NOT EXISTS customers
(
    customer_id    uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    customer_type  text        NOT NULL CHECK (customer_type IN ('PRIVATE', 'COMPANY')),
    email          text        NOT NULL,
    phone          text,

    -- per PRIVATE
    first_name     text,
    last_name      text,

    -- per COMPANY
    company_name   text,
    contact_person text,

    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_customers_email
    ON customers (lower(email));

-- =========================
-- QUOTE SESSIONS (carrello preventivo)
-- =========================
CREATE TABLE IF NOT EXISTS quote_sessions
(
    quote_session_id   uuid PRIMARY KEY        DEFAULT gen_random_uuid(),
    status             text           NOT NULL CHECK (status IN ('ACTIVE', 'CAD_ACTIVE', 'EXPIRED', 'CONVERTED')),
    pricing_version    text           NOT NULL,

    -- Parametri "globali" (dalla tua UI avanzata)
    material_code      text           NOT NULL, -- es: PLA, PETG...
    nozzle_diameter_mm numeric(5, 2),           -- es: 0.40
    layer_height_mm    numeric(6, 3),           -- es: 0.20
    infill_pattern     text,                    -- es: grid
    infill_percent     integer CHECK (infill_percent BETWEEN 0 AND 100),
    supports_enabled   boolean        NOT NULL DEFAULT false,
    notes              text,

    setup_cost_chf     numeric(12, 2) NOT NULL DEFAULT 0.00,
    source_request_id  uuid,
    cad_hours          numeric(10, 2),
    cad_hourly_rate_chf numeric(10, 2),

    created_at         timestamptz    NOT NULL DEFAULT now(),
    expires_at         timestamptz    NOT NULL,
    converted_order_id uuid
);

CREATE INDEX IF NOT EXISTS ix_quote_sessions_status
    ON quote_sessions (status);

CREATE INDEX IF NOT EXISTS ix_quote_sessions_expires_at
    ON quote_sessions (expires_at);

CREATE INDEX IF NOT EXISTS ix_quote_sessions_source_request
    ON quote_sessions (source_request_id);

ALTER TABLE quote_sessions
    ADD COLUMN IF NOT EXISTS source_request_id uuid;

ALTER TABLE quote_sessions
    ADD COLUMN IF NOT EXISTS cad_hours numeric(10, 2);

ALTER TABLE quote_sessions
    ADD COLUMN IF NOT EXISTS cad_hourly_rate_chf numeric(10, 2);

ALTER TABLE quote_sessions
    DROP CONSTRAINT IF EXISTS quote_sessions_status_check;

ALTER TABLE quote_sessions
    ADD CONSTRAINT quote_sessions_status_check
        CHECK (status IN ('ACTIVE', 'CAD_ACTIVE', 'EXPIRED', 'CONVERTED'));

-- =========================
-- QUOTE LINE ITEMS (1 file = 1 riga)
-- =========================
CREATE TABLE IF NOT EXISTS quote_line_items
(
    quote_line_item_id uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    quote_session_id   uuid        NOT NULL REFERENCES quote_sessions (quote_session_id) ON DELETE CASCADE,

    status             text        NOT NULL CHECK (status IN ('CALCULATING', 'READY', 'FAILED', 'REVIEW_REQUIRED')),

    original_filename  text        NOT NULL,
    quantity           integer     NOT NULL DEFAULT 1 CHECK (quantity >= 1),
    color_code         text,  -- es: white/black o codice interno
    filament_variant_id bigint REFERENCES filament_variant (filament_variant_id),
    material_code      text,
    nozzle_diameter_mm numeric(5, 2),
    layer_height_mm    numeric(6, 3),
    infill_pattern     text,
    infill_percent     integer CHECK (infill_percent BETWEEN 0 AND 100),
    supports_enabled   boolean,

    -- Output slicing / calcolo
    bounding_box_x_mm  numeric(10, 3),
    bounding_box_y_mm  numeric(10, 3),
    bounding_box_z_mm  numeric(10, 3),
    print_time_seconds integer CHECK (print_time_seconds >= 0),
    material_grams     numeric(12, 2) CHECK (material_grams >= 0),

    unit_price_chf     numeric(12, 2) CHECK (unit_price_chf >= 0),
    pricing_breakdown  jsonb, -- opzionale: costi dettagliati senza creare tabelle

    error_message      text,

    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_quote_line_items_session
    ON quote_line_items (quote_session_id);

-- Keep files that cannot be priced automatically available for manual review.
-- PostgreSQL check constraints are not updated by Hibernate ddl-auto=update.
ALTER TABLE quote_line_items
    DROP CONSTRAINT IF EXISTS quote_line_items_status_check;

ALTER TABLE quote_line_items
    ADD CONSTRAINT quote_line_items_status_check
        CHECK (status IN ('CALCULATING', 'READY', 'FAILED', 'REVIEW_REQUIRED'));

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS material_code text;

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS nozzle_diameter_mm numeric(5, 2);

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS layer_height_mm numeric(6, 3);

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS infill_pattern text;

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS infill_percent integer;

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS supports_enabled boolean;

-- Vista utile per totale quote
CREATE OR REPLACE VIEW quote_session_totals AS
SELECT qs.quote_session_id,
       qs.setup_cost_chf +
       COALESCE(SUM(qli.unit_price_chf * qli.quantity), 0.00) AS total_chf
FROM quote_sessions qs
         LEFT JOIN quote_line_items qli
                   ON qli.quote_session_id = qs.quote_session_id
                       AND qli.status = 'READY'
GROUP BY qs.quote_session_id;

-- =========================
-- ORDERS
-- =========================
CREATE TABLE IF NOT EXISTS orders
(
    order_id                 uuid PRIMARY KEY        DEFAULT gen_random_uuid(),
    source_quote_session_id  uuid REFERENCES quote_sessions (quote_session_id),
    source_request_id        uuid,

    status                   text           NOT NULL CHECK (status IN (
                                                                       'PENDING_PAYMENT', 'PAID', 'IN_PRODUCTION',
                                                                       'SHIPPED', 'COMPLETED', 'CANCELLED'
        )),

    customer_id              uuid REFERENCES customers (customer_id),
    customer_email           text           NOT NULL,
    customer_phone           text,
    preferred_language       char(2)        NOT NULL DEFAULT 'it',

    -- Snapshot indirizzo/fatturazione (evita tabella addresses e mantiene storico)
    billing_customer_type    text           NOT NULL CHECK (billing_customer_type IN ('PRIVATE', 'COMPANY')),
    billing_first_name       text,
    billing_last_name        text,
    billing_company_name     text,
    billing_contact_person   text,

    billing_address_line1    text           NOT NULL,
    billing_address_line2    text,
    billing_zip              text           NOT NULL,
    billing_city             text           NOT NULL,
    billing_country_code     char(2)        NOT NULL DEFAULT 'CH',

    shipping_same_as_billing boolean        NOT NULL DEFAULT true,
    shipping_first_name      text,
    shipping_last_name       text,
    shipping_company_name    text,
    shipping_contact_person  text,
    shipping_address_line1   text,
    shipping_address_line2   text,
    shipping_zip             text,
    shipping_city            text,
    shipping_country_code    char(2),

    currency                 char(3)        NOT NULL DEFAULT 'CHF',
    setup_cost_chf           numeric(12, 2) NOT NULL DEFAULT 0.00,
    shipping_cost_chf        numeric(12, 2) NOT NULL DEFAULT 0.00,
    shipping_quote_snapshot  jsonb,
    discount_chf             numeric(12, 2) NOT NULL DEFAULT 0.00,

    subtotal_chf             numeric(12, 2) NOT NULL DEFAULT 0.00,
    is_cad_order             boolean        NOT NULL DEFAULT false,
    cad_hours                numeric(10, 2),
    cad_hourly_rate_chf      numeric(10, 2),
    cad_total_chf            numeric(12, 2) NOT NULL DEFAULT 0.00,
    total_chf                numeric(12, 2) NOT NULL DEFAULT 0.00,

    created_at               timestamptz    NOT NULL DEFAULT now(),
    updated_at               timestamptz    NOT NULL DEFAULT now(),
    paid_at                  timestamptz
);

CREATE INDEX IF NOT EXISTS ix_orders_status
    ON orders (status);

CREATE INDEX IF NOT EXISTS ix_orders_customer_email
    ON orders (lower(customer_email));

CREATE INDEX IF NOT EXISTS ix_orders_source_request
    ON orders (source_request_id);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS source_request_id uuid;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS is_cad_order boolean NOT NULL DEFAULT false;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS cad_hours numeric(10, 2);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS cad_hourly_rate_chf numeric(10, 2);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS cad_total_chf numeric(12, 2) NOT NULL DEFAULT 0.00;

-- =========================
-- ORDER ITEMS (1 file 3D = 1 riga, file salvato su disco)
-- =========================
CREATE TABLE IF NOT EXISTS order_items
(
    order_item_id        uuid PRIMARY KEY        DEFAULT gen_random_uuid(),
    order_id             uuid           NOT NULL REFERENCES orders (order_id) ON DELETE CASCADE,

    original_filename    text           NOT NULL,
    stored_relative_path text           NOT NULL, -- es: orders/<orderId>/3d-files/<orderItemId>/<uuid>.stl
    stored_filename      text           NOT NULL, -- es: <uuid>.stl

    file_size_bytes      bigint CHECK (file_size_bytes >= 0),
    mime_type            text,
    sha256_hex           text,                    -- opzionale, utile anche per dedup interno

    material_code        text           NOT NULL,
    filament_variant_id  bigint REFERENCES filament_variant (filament_variant_id),
    color_code           text,
    quantity             integer        NOT NULL DEFAULT 1 CHECK (quantity >= 1),

    -- Snapshot output
    print_time_seconds   integer CHECK (print_time_seconds >= 0),
    material_grams       numeric(12, 2) CHECK (material_grams >= 0),

    bounding_box_x_mm numeric(10, 3),
    bounding_box_y_mm numeric(10, 3),
    bounding_box_z_mm numeric(10, 3),

    unit_price_chf       numeric(12, 2) NOT NULL CHECK (unit_price_chf >= 0),
    line_total_chf       numeric(12, 2) NOT NULL CHECK (line_total_chf >= 0),

    created_at           timestamptz    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_order_items_order
    ON order_items (order_id);

-- =========================
-- PAYMENTS (supporta più tentativi / metodi)
-- =========================
CREATE TABLE IF NOT EXISTS payments
(
    payment_id              uuid PRIMARY KEY        DEFAULT gen_random_uuid(),
    order_id                uuid           NOT NULL REFERENCES orders (order_id) ON DELETE CASCADE,

    method                  text           NOT NULL CHECK (method IN ('QR_BILL', 'TWINT', 'OTHER')),
    status                  text           NOT NULL CHECK (status IN ('PENDING', 'REPORTED', 'RECEIVED', 'FAILED', 'CANCELLED', 'REFUNDED')),

    currency                char(3)        NOT NULL DEFAULT 'CHF',
    amount_chf              numeric(12, 2) NOT NULL CHECK (amount_chf >= 0),

    -- riferimento pagamento (molto utile per QR bill / riconciliazione)
    payment_reference       text,
    provider_transaction_id text,

    qr_payload              text, -- se vuoi salvare contenuto QR/Swiss QR bill
    initiated_at            timestamptz    NOT NULL DEFAULT now(),
    reported_at             timestamptz,
    received_at             timestamptz
);

CREATE INDEX IF NOT EXISTS ix_payments_order
    ON payments (order_id);

CREATE INDEX IF NOT EXISTS ix_payments_reference
    ON payments (payment_reference);

-- =========================
-- CUSTOM QUOTE REQUESTS (preventivo personalizzato, form che hai mostrato)
-- =========================
CREATE TABLE IF NOT EXISTS custom_quote_requests
(
    request_id     uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    request_type   text        NOT NULL, -- es: "PREVENTIVO_PERSONALIZZATO" o come preferisci

    customer_type  text        NOT NULL CHECK (customer_type IN ('PRIVATE', 'COMPANY')),
    email          text        NOT NULL,
    phone          text,

    -- PRIVATE
    name           text,

    -- COMPANY
    company_name   text,
    contact_person text,

    message        text        NOT NULL,
    status         text        NOT NULL CHECK (status IN ('NEW', 'PENDING', 'IN_PROGRESS', 'DONE', 'CLOSED')),

    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_custom_quote_requests_status
    ON custom_quote_requests (status);

CREATE INDEX IF NOT EXISTS ix_custom_quote_requests_email
    ON custom_quote_requests (lower(email));

-- Allegati della richiesta (max 15 come UI)
CREATE TABLE IF NOT EXISTS custom_quote_request_attachments
(
    attachment_id        uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    request_id           uuid        NOT NULL REFERENCES custom_quote_requests (request_id) ON DELETE CASCADE,

    original_filename    text        NOT NULL,
    stored_relative_path text        NOT NULL, -- es: quote-requests/<requestId>/attachments/<attachmentId>/<uuid>.stl
    stored_filename      text        NOT NULL,

    file_size_bytes      bigint CHECK (file_size_bytes >= 0),
    mime_type            text,
    sha256_hex           text,

    created_at           timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_custom_quote_attachments_request
    ON custom_quote_request_attachments (request_id);

CREATE TABLE IF NOT EXISTS media_asset
(
    media_asset_id     uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    original_filename  text        NOT NULL,
    storage_key        text        NOT NULL UNIQUE,
    mime_type          text        NOT NULL,
    file_size_bytes    bigint      NOT NULL CHECK (file_size_bytes >= 0),
    sha256_hex         text        NOT NULL,
    width_px           integer,
    height_px          integer,
    status             text        NOT NULL CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED', 'ARCHIVED')),
    visibility         text        NOT NULL CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    title              text,
    alt_text           text,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_media_asset_status_visibility_created_at
    ON media_asset (status, visibility, created_at DESC);

CREATE TABLE IF NOT EXISTS media_variant
(
    media_variant_id  uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    media_asset_id    uuid        NOT NULL REFERENCES media_asset (media_asset_id) ON DELETE CASCADE,
    variant_name      text        NOT NULL,
    format            text        NOT NULL CHECK (format IN ('ORIGINAL', 'JPEG', 'WEBP', 'AVIF')),
    storage_key       text        NOT NULL UNIQUE,
    mime_type         text        NOT NULL,
    width_px          integer     NOT NULL,
    height_px         integer     NOT NULL,
    file_size_bytes   bigint      NOT NULL CHECK (file_size_bytes >= 0),
    is_generated      boolean     NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_media_variant_asset_name_format UNIQUE (media_asset_id, variant_name, format)
);

CREATE INDEX IF NOT EXISTS ix_media_variant_asset
    ON media_variant (media_asset_id);

CREATE TABLE IF NOT EXISTS media_usage
(
    media_usage_id    uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    usage_type        text        NOT NULL,
    usage_key         text        NOT NULL,
    owner_id          uuid,
    media_asset_id    uuid        NOT NULL REFERENCES media_asset (media_asset_id) ON DELETE CASCADE,
    sort_order        integer     NOT NULL DEFAULT 0,
    is_primary        boolean     NOT NULL DEFAULT false,
    is_active         boolean     NOT NULL DEFAULT true,
    title_it          text,
    title_en          text,
    title_de          text,
    title_fr          text,
    alt_text_it       text,
    alt_text_en       text,
    alt_text_de       text,
    alt_text_fr       text,
    created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_media_usage_scope
    ON media_usage (usage_type, usage_key, is_active, sort_order);

ALTER TABLE media_usage
    ADD COLUMN IF NOT EXISTS title_it text;

ALTER TABLE media_usage
    ADD COLUMN IF NOT EXISTS title_en text;

ALTER TABLE media_usage
    ADD COLUMN IF NOT EXISTS title_de text;

ALTER TABLE media_usage
    ADD COLUMN IF NOT EXISTS title_fr text;

ALTER TABLE media_usage
    ADD COLUMN IF NOT EXISTS alt_text_it text;

ALTER TABLE media_usage
    ADD COLUMN IF NOT EXISTS alt_text_en text;

ALTER TABLE media_usage
    ADD COLUMN IF NOT EXISTS alt_text_de text;

ALTER TABLE media_usage
    ADD COLUMN IF NOT EXISTS alt_text_fr text;

CREATE TABLE IF NOT EXISTS home_project
(
    home_project_id uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    slug            text        NOT NULL UNIQUE,
    eyebrow_it      text,
    eyebrow_en      text,
    eyebrow_de      text,
    eyebrow_fr      text,
    title_it        text,
    title_en        text,
    title_de        text,
    title_fr        text,
    description_it  text,
    description_en  text,
    description_de  text,
    description_fr  text,
    is_active       boolean     NOT NULL DEFAULT true,
    sort_order      integer     NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_home_project_active_sort
    ON home_project (is_active, sort_order);

ALTER TABLE home_project
    ADD COLUMN IF NOT EXISTS eyebrow_it text;

ALTER TABLE home_project
    ADD COLUMN IF NOT EXISTS eyebrow_en text;

ALTER TABLE home_project
    ADD COLUMN IF NOT EXISTS eyebrow_de text;

ALTER TABLE home_project
    ADD COLUMN IF NOT EXISTS eyebrow_fr text;

ALTER TABLE home_project
    ADD COLUMN IF NOT EXISTS title_it text;

ALTER TABLE home_project
    ADD COLUMN IF NOT EXISTS title_en text;

ALTER TABLE home_project
    ADD COLUMN IF NOT EXISTS title_de text;

ALTER TABLE home_project
    ADD COLUMN IF NOT EXISTS title_fr text;

ALTER TABLE home_project
    ADD COLUMN IF NOT EXISTS description_it text;

ALTER TABLE home_project
    ADD COLUMN IF NOT EXISTS description_en text;

ALTER TABLE home_project
    ADD COLUMN IF NOT EXISTS description_de text;

ALTER TABLE home_project
    ADD COLUMN IF NOT EXISTS description_fr text;

CREATE TABLE IF NOT EXISTS shop_category
(
    shop_category_id    uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    parent_category_id  uuid REFERENCES shop_category (shop_category_id) ON DELETE SET NULL,
    slug                text        NOT NULL UNIQUE,
    name                text        NOT NULL,
    name_it             text,
    name_en             text,
    name_de             text,
    name_fr             text,
    description         text,
    description_it      text,
    description_en      text,
    description_de      text,
    description_fr      text,
    seo_title           text,
    seo_title_it        text,
    seo_title_en        text,
    seo_title_de        text,
    seo_title_fr        text,
    seo_description     text,
    seo_description_it  text,
    seo_description_en  text,
    seo_description_de  text,
    seo_description_fr  text,
    og_title            text,
    og_description      text,
    indexable           boolean     NOT NULL DEFAULT true,
    is_active           boolean     NOT NULL DEFAULT true,
    sort_order          integer     NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_shop_category_not_self_parent CHECK (
        parent_category_id IS NULL OR parent_category_id <> shop_category_id
    )
);

CREATE INDEX IF NOT EXISTS ix_shop_category_parent_sort
    ON shop_category (parent_category_id, sort_order, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_shop_category_active_sort
    ON shop_category (is_active, sort_order, created_at DESC);

ALTER TABLE shop_category
    ADD COLUMN IF NOT EXISTS name_it text,
    ADD COLUMN IF NOT EXISTS name_en text,
    ADD COLUMN IF NOT EXISTS name_de text,
    ADD COLUMN IF NOT EXISTS name_fr text,
    ADD COLUMN IF NOT EXISTS description_it text,
    ADD COLUMN IF NOT EXISTS description_en text,
    ADD COLUMN IF NOT EXISTS description_de text,
    ADD COLUMN IF NOT EXISTS description_fr text,
    ADD COLUMN IF NOT EXISTS seo_title_it text,
    ADD COLUMN IF NOT EXISTS seo_title_en text,
    ADD COLUMN IF NOT EXISTS seo_title_de text,
    ADD COLUMN IF NOT EXISTS seo_title_fr text,
    ADD COLUMN IF NOT EXISTS seo_description_it text,
    ADD COLUMN IF NOT EXISTS seo_description_en text,
    ADD COLUMN IF NOT EXISTS seo_description_de text,
    ADD COLUMN IF NOT EXISTS seo_description_fr text;

UPDATE shop_category
SET
    name_it = COALESCE(NULLIF(btrim(name_it), ''), name),
    name_en = COALESCE(NULLIF(btrim(name_en), ''), name),
    name_de = COALESCE(NULLIF(btrim(name_de), ''), name),
    name_fr = COALESCE(NULLIF(btrim(name_fr), ''), name),
    description_it = COALESCE(NULLIF(btrim(description_it), ''), description),
    description_en = COALESCE(NULLIF(btrim(description_en), ''), description),
    description_de = COALESCE(NULLIF(btrim(description_de), ''), description),
    description_fr = COALESCE(NULLIF(btrim(description_fr), ''), description),
    seo_title_it = COALESCE(NULLIF(btrim(seo_title_it), ''), seo_title),
    seo_title_en = COALESCE(NULLIF(btrim(seo_title_en), ''), seo_title),
    seo_title_de = COALESCE(NULLIF(btrim(seo_title_de), ''), seo_title),
    seo_title_fr = COALESCE(NULLIF(btrim(seo_title_fr), ''), seo_title),
    seo_description_it = COALESCE(NULLIF(btrim(seo_description_it), ''), seo_description),
    seo_description_en = COALESCE(NULLIF(btrim(seo_description_en), ''), seo_description),
    seo_description_de = COALESCE(NULLIF(btrim(seo_description_de), ''), seo_description),
    seo_description_fr = COALESCE(NULLIF(btrim(seo_description_fr), ''), seo_description)
WHERE
    NULLIF(btrim(name_it), '') IS NULL
    OR NULLIF(btrim(name_en), '') IS NULL
    OR NULLIF(btrim(name_de), '') IS NULL
    OR NULLIF(btrim(name_fr), '') IS NULL
    OR (description IS NOT NULL AND (
        NULLIF(btrim(description_it), '') IS NULL
        OR NULLIF(btrim(description_en), '') IS NULL
        OR NULLIF(btrim(description_de), '') IS NULL
        OR NULLIF(btrim(description_fr), '') IS NULL
    ))
    OR (seo_title IS NOT NULL AND (
        NULLIF(btrim(seo_title_it), '') IS NULL
        OR NULLIF(btrim(seo_title_en), '') IS NULL
        OR NULLIF(btrim(seo_title_de), '') IS NULL
        OR NULLIF(btrim(seo_title_fr), '') IS NULL
    ))
    OR (seo_description IS NOT NULL AND (
        NULLIF(btrim(seo_description_it), '') IS NULL
        OR NULLIF(btrim(seo_description_en), '') IS NULL
        OR NULLIF(btrim(seo_description_de), '') IS NULL
        OR NULLIF(btrim(seo_description_fr), '') IS NULL
    ));

CREATE TABLE IF NOT EXISTS shop_product
(
    shop_product_id     uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    shop_category_id    uuid        NOT NULL REFERENCES shop_category (shop_category_id),
    slug                text        NOT NULL UNIQUE,
    name                text        NOT NULL,
    name_it             text,
    name_en             text,
    name_de             text,
    name_fr             text,
    excerpt             text,
    excerpt_it          text,
    excerpt_en          text,
    excerpt_de          text,
    excerpt_fr          text,
    description         text,
    description_it      text,
    description_en      text,
    description_de      text,
    description_fr      text,
    seo_title           text,
    seo_title_it        text,
    seo_title_en        text,
    seo_title_de        text,
    seo_title_fr        text,
    seo_description     text,
    seo_description_it  text,
    seo_description_en  text,
    seo_description_de  text,
    seo_description_fr  text,
    og_title            text,
    og_description      text,
    indexable           boolean     NOT NULL DEFAULT true,
    is_featured         boolean     NOT NULL DEFAULT false,
    is_active           boolean     NOT NULL DEFAULT true,
    sort_order          integer     NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE shop_product
    ADD COLUMN IF NOT EXISTS seo_title_it text,
    ADD COLUMN IF NOT EXISTS seo_title_en text,
    ADD COLUMN IF NOT EXISTS seo_title_de text,
    ADD COLUMN IF NOT EXISTS seo_title_fr text,
    ADD COLUMN IF NOT EXISTS seo_description_it text,
    ADD COLUMN IF NOT EXISTS seo_description_en text,
    ADD COLUMN IF NOT EXISTS seo_description_de text,
    ADD COLUMN IF NOT EXISTS seo_description_fr text;

CREATE INDEX IF NOT EXISTS ix_shop_product_category_active_sort
    ON shop_product (shop_category_id, is_active, sort_order, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_shop_product_featured_sort
    ON shop_product (is_featured, is_active, sort_order, created_at DESC);

ALTER TABLE shop_product
    ADD COLUMN IF NOT EXISTS name_it text;

ALTER TABLE shop_product
    ADD COLUMN IF NOT EXISTS name_en text;

ALTER TABLE shop_product
    ADD COLUMN IF NOT EXISTS name_de text;

ALTER TABLE shop_product
    ADD COLUMN IF NOT EXISTS name_fr text;

ALTER TABLE shop_product
    ADD COLUMN IF NOT EXISTS excerpt_it text;

ALTER TABLE shop_product
    ADD COLUMN IF NOT EXISTS excerpt_en text;

ALTER TABLE shop_product
    ADD COLUMN IF NOT EXISTS excerpt_de text;

ALTER TABLE shop_product
    ADD COLUMN IF NOT EXISTS excerpt_fr text;

ALTER TABLE shop_product
    ADD COLUMN IF NOT EXISTS description_it text;

ALTER TABLE shop_product
    ADD COLUMN IF NOT EXISTS description_en text;

ALTER TABLE shop_product
    ADD COLUMN IF NOT EXISTS description_de text;

ALTER TABLE shop_product
    ADD COLUMN IF NOT EXISTS description_fr text;

UPDATE shop_product
SET
    name_it = COALESCE(NULLIF(btrim(name_it), ''), name),
    name_en = COALESCE(NULLIF(btrim(name_en), ''), name),
    name_de = COALESCE(NULLIF(btrim(name_de), ''), name),
    name_fr = COALESCE(NULLIF(btrim(name_fr), ''), name),
    excerpt_it = COALESCE(NULLIF(btrim(excerpt_it), ''), excerpt),
    excerpt_en = COALESCE(NULLIF(btrim(excerpt_en), ''), excerpt),
    excerpt_de = COALESCE(NULLIF(btrim(excerpt_de), ''), excerpt),
    excerpt_fr = COALESCE(NULLIF(btrim(excerpt_fr), ''), excerpt),
    description_it = COALESCE(NULLIF(btrim(description_it), ''), description),
    description_en = COALESCE(NULLIF(btrim(description_en), ''), description),
    description_de = COALESCE(NULLIF(btrim(description_de), ''), description),
    description_fr = COALESCE(NULLIF(btrim(description_fr), ''), description)
WHERE
    NULLIF(btrim(name_it), '') IS NULL
    OR NULLIF(btrim(name_en), '') IS NULL
    OR NULLIF(btrim(name_de), '') IS NULL
    OR NULLIF(btrim(name_fr), '') IS NULL
    OR (excerpt IS NOT NULL AND (
        NULLIF(btrim(excerpt_it), '') IS NULL
        OR NULLIF(btrim(excerpt_en), '') IS NULL
        OR NULLIF(btrim(excerpt_de), '') IS NULL
        OR NULLIF(btrim(excerpt_fr), '') IS NULL
    ))
    OR (description IS NOT NULL AND (
        NULLIF(btrim(description_it), '') IS NULL
        OR NULLIF(btrim(description_en), '') IS NULL
        OR NULLIF(btrim(description_de), '') IS NULL
        OR NULLIF(btrim(description_fr), '') IS NULL
    ));

CREATE TABLE IF NOT EXISTS shop_product_variant
(
    shop_product_variant_id uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    shop_product_id         uuid        NOT NULL REFERENCES shop_product (shop_product_id) ON DELETE CASCADE,
    sku                     text UNIQUE,
    variant_label           text        NOT NULL,
    color_name              text        NOT NULL,
    color_label_it          text,
    color_label_en          text,
    color_label_de          text,
    color_label_fr          text,
    color_hex               text,
    internal_material_code  text        NOT NULL,
    price_chf               numeric(12, 2) NOT NULL DEFAULT 0.00 CHECK (price_chf >= 0),
    is_default              boolean     NOT NULL DEFAULT false,
    is_active               boolean     NOT NULL DEFAULT true,
    sort_order              integer     NOT NULL DEFAULT 0,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_shop_product_variant_product_active_sort
    ON shop_product_variant (shop_product_id, is_active, sort_order, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_shop_product_variant_sku
    ON shop_product_variant (sku);

ALTER TABLE shop_product_variant
    ADD COLUMN IF NOT EXISTS color_label_it text,
    ADD COLUMN IF NOT EXISTS color_label_en text,
    ADD COLUMN IF NOT EXISTS color_label_de text,
    ADD COLUMN IF NOT EXISTS color_label_fr text;

UPDATE shop_product_variant
SET color_label_it = COALESCE(NULLIF(btrim(color_label_it), ''), color_name),
    color_label_en = COALESCE(NULLIF(btrim(color_label_en), ''), color_name),
    color_label_de = COALESCE(NULLIF(btrim(color_label_de), ''), color_name),
    color_label_fr = COALESCE(NULLIF(btrim(color_label_fr), ''), color_name)
WHERE NULLIF(btrim(color_label_it), '') IS NULL
   OR NULLIF(btrim(color_label_en), '') IS NULL
   OR NULLIF(btrim(color_label_de), '') IS NULL
   OR NULLIF(btrim(color_label_fr), '') IS NULL;

CREATE TABLE IF NOT EXISTS shop_product_model_asset
(
    shop_product_model_asset_id uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    shop_product_id             uuid        NOT NULL UNIQUE REFERENCES shop_product (shop_product_id) ON DELETE CASCADE,
    original_filename           text        NOT NULL,
    stored_relative_path        text        NOT NULL,
    stored_filename             text        NOT NULL,
    file_size_bytes             bigint CHECK (file_size_bytes >= 0),
    mime_type                   text,
    sha256_hex                  text,
    bounding_box_x_mm           numeric(10, 3),
    bounding_box_y_mm           numeric(10, 3),
    bounding_box_z_mm           numeric(10, 3),
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_shop_product_model_asset_product
    ON shop_product_model_asset (shop_product_id);

ALTER TABLE quote_sessions
    ADD COLUMN IF NOT EXISTS session_type text NOT NULL DEFAULT 'PRINT_QUOTE';

CREATE INDEX IF NOT EXISTS ix_quote_sessions_session_type
    ON quote_sessions (session_type);

ALTER TABLE quote_sessions
    DROP CONSTRAINT IF EXISTS quote_sessions_session_type_check;

ALTER TABLE quote_sessions
    ADD CONSTRAINT quote_sessions_session_type_check
        CHECK (session_type IN ('PRINT_QUOTE', 'SHOP_CART'));

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS line_item_type text NOT NULL DEFAULT 'PRINT_FILE';

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS display_name text;

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS shop_product_id uuid;

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS shop_product_variant_id uuid;

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS shop_product_slug text;

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS shop_product_name text;

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS shop_variant_label text;

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS shop_variant_color_name text;

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS shop_variant_color_hex text;

ALTER TABLE quote_line_items
    ADD COLUMN IF NOT EXISTS stored_path text;

CREATE INDEX IF NOT EXISTS ix_quote_line_items_shop_product
    ON quote_line_items (shop_product_id);

CREATE INDEX IF NOT EXISTS ix_quote_line_items_shop_product_variant
    ON quote_line_items (shop_product_variant_id);

ALTER TABLE quote_line_items
    DROP CONSTRAINT IF EXISTS quote_line_items_line_item_type_check;

ALTER TABLE quote_line_items
    ADD CONSTRAINT quote_line_items_line_item_type_check
        CHECK (line_item_type IN ('PRINT_FILE', 'SHOP_PRODUCT'));

ALTER TABLE quote_line_items
    DROP CONSTRAINT IF EXISTS fk_quote_line_items_shop_product;

ALTER TABLE quote_line_items
    ADD CONSTRAINT fk_quote_line_items_shop_product
        FOREIGN KEY (shop_product_id) REFERENCES shop_product (shop_product_id);

ALTER TABLE quote_line_items
    DROP CONSTRAINT IF EXISTS fk_quote_line_items_shop_product_variant;

ALTER TABLE quote_line_items
    ADD CONSTRAINT fk_quote_line_items_shop_product_variant
        FOREIGN KEY (shop_product_variant_id) REFERENCES shop_product_variant (shop_product_variant_id);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS source_type text NOT NULL DEFAULT 'CALCULATOR';

CREATE INDEX IF NOT EXISTS ix_orders_source_type
    ON orders (source_type);

ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS orders_source_type_check;

ALTER TABLE orders
    ADD CONSTRAINT orders_source_type_check
        CHECK (source_type IN ('CALCULATOR', 'SHOP'));

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS item_type text NOT NULL DEFAULT 'PRINT_FILE';

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS display_name text;

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS shop_product_id uuid;

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS shop_product_variant_id uuid;

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS shop_product_slug text;

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS shop_product_name text;

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS shop_variant_label text;

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS shop_variant_color_name text;

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS shop_variant_color_hex text;

CREATE INDEX IF NOT EXISTS ix_order_items_shop_product
    ON order_items (shop_product_id);

CREATE INDEX IF NOT EXISTS ix_order_items_shop_product_variant
    ON order_items (shop_product_variant_id);

ALTER TABLE order_items
    DROP CONSTRAINT IF EXISTS order_items_item_type_check;

ALTER TABLE order_items
    ADD CONSTRAINT order_items_item_type_check
        CHECK (item_type IN ('PRINT_FILE', 'SHOP_PRODUCT'));

ALTER TABLE order_items
    DROP CONSTRAINT IF EXISTS fk_order_items_shop_product;

ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_shop_product
        FOREIGN KEY (shop_product_id) REFERENCES shop_product (shop_product_id);

ALTER TABLE order_items
    DROP CONSTRAINT IF EXISTS fk_order_items_shop_product_variant;

ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_shop_product_variant
        FOREIGN KEY (shop_product_variant_id) REFERENCES shop_product_variant (shop_product_variant_id);

ALTER TABLE quote_sessions
    DROP CONSTRAINT IF EXISTS fk_quote_sessions_source_request;

ALTER TABLE quote_sessions
    ADD CONSTRAINT fk_quote_sessions_source_request
        FOREIGN KEY (source_request_id) REFERENCES custom_quote_requests (request_id);

ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS fk_orders_source_request;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_source_request
        FOREIGN KEY (source_request_id) REFERENCES custom_quote_requests (request_id);
