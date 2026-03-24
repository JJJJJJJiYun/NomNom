create table if not exists app_user (
    id uuid primary key,
    status varchar(16) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists user_device (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    installation_id uuid not null,
    device_name varchar(128),
    app_version varchar(32),
    last_seen_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    unique (installation_id)
);

create table if not exists auth_identity (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    provider varchar(32) not null,
    provider_subject varchar(255) not null,
    created_at timestamptz not null default now(),
    unique (provider, provider_subject)
);

create table if not exists venue (
    id uuid primary key,
    name varchar(255) not null,
    normalized_name varchar(255),
    city_code varchar(32),
    district varchar(64),
    business_area varchar(64),
    address varchar(255),
    latitude numeric(10, 6),
    longitude numeric(10, 6),
    category varchar(64),
    subcategory varchar(64),
    avg_price integer,
    rating numeric(3, 2),
    review_count integer,
    open_status varchar(16),
    phone varchar(64),
    cover_image_url text,
    tags jsonb not null default '[]'::jsonb,
    source_provider varchar(32) not null,
    source_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_venue_city_category on venue(city_code, category);
create index if not exists idx_venue_district_business_area on venue(district, business_area);
create index if not exists idx_venue_rating on venue(rating desc);
create index if not exists idx_venue_avg_price on venue(avg_price);

create table if not exists venue_source_ref (
    id uuid primary key,
    venue_id uuid not null references venue(id),
    provider varchar(32) not null,
    external_id varchar(255),
    source_url text,
    raw_payload jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (provider, external_id)
);

create table if not exists user_list (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    name varchar(64) not null,
    list_type varchar(32) not null default 'FAVORITES',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists idx_user_default_list on user_list(user_id, list_type);

create table if not exists user_list_item (
    id uuid primary key,
    list_id uuid not null references user_list(id),
    venue_id uuid not null references venue(id),
    source_provider varchar(32),
    source_import_job_id uuid,
    note varchar(255),
    pinned boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (list_id, venue_id)
);

create index if not exists idx_user_list_item_list_created on user_list_item(list_id, created_at desc);

create table if not exists import_job (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    source_provider varchar(32) not null,
    shared_text text,
    shared_url text,
    parsed_name varchar(255),
    parsed_external_id varchar(255),
    status varchar(32) not null,
    failure_reason varchar(255),
    venue_id uuid references venue(id),
    raw_parse_result jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_import_job_user_created on import_job(user_id, created_at desc);
create index if not exists idx_import_job_status on import_job(status);

create table if not exists decision_session (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    name varchar(128) not null,
    status varchar(32) not null,
    people_count integer,
    price_min integer,
    price_max integer,
    open_now boolean,
    district varchar(64),
    business_area varchar(64),
    started_at timestamptz,
    completed_at timestamptz,
    winner_venue_id uuid references venue(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_decision_session_user_created on decision_session(user_id, created_at desc);
create index if not exists idx_decision_session_status on decision_session(status);

create table if not exists decision_session_candidate (
    id uuid primary key,
    session_id uuid not null references decision_session(id),
    venue_id uuid not null references venue(id),
    seed integer not null,
    initial_score numeric(10, 4) not null,
    eliminated boolean not null default false,
    eliminated_round integer,
    created_at timestamptz not null default now(),
    unique (session_id, venue_id)
);

create index if not exists idx_decision_candidate_session_seed on decision_session_candidate(session_id, seed);

create table if not exists decision_matchup (
    id uuid primary key,
    session_id uuid not null references decision_session(id),
    round_no integer not null,
    bracket_order integer not null,
    left_venue_id uuid not null references venue(id),
    right_venue_id uuid references venue(id),
    winner_venue_id uuid references venue(id),
    status varchar(16) not null,
    decided_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists idx_matchup_session_round_order on decision_matchup(session_id, round_no, bracket_order);
create index if not exists idx_matchup_session_status on decision_matchup(session_id, status);

create table if not exists decision_vote (
    id uuid primary key,
    matchup_id uuid not null references decision_matchup(id),
    session_id uuid not null references decision_session(id),
    user_id uuid not null references app_user(id),
    winner_venue_id uuid not null references venue(id),
    created_at timestamptz not null default now(),
    unique (matchup_id, user_id)
);
