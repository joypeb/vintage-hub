create table crawl_site (
	id bigserial primary key,
	code varchar(100) not null unique,
	display_name varchar(255) not null,
	base_url varchar(500) not null,
	platform varchar(100) not null,
	crawl_interval_minutes integer not null,
	crawler_status varchar(50) not null,
	last_crawled_at timestamptz,
	last_changed_at timestamptz,
	last_change_detected_at timestamptz,
	consecutive_no_change_count integer not null default 0
);

create table crawl_run (
	id bigserial primary key,
	site_id bigint not null references crawl_site(id),
	trigger_type varchar(50) not null,
	status varchar(50) not null,
	started_at timestamptz,
	finished_at timestamptz,
	found_count integer not null default 0,
	created_count integer not null default 0,
	updated_count integer not null default 0,
	failed_count integer not null default 0,
	message varchar(1000)
);

create table product (
	id bigserial primary key,
	site_id bigint not null references crawl_site(id),
	source_product_id varchar(100) not null,
	name varchar(500) not null,
	original_price numeric(12, 2),
	sale_price numeric(12, 2),
	stock_status varchar(50) not null,
	description text,
	detail_url varchar(1000) not null,
	thumbnail_image_url varchar(1000),
	source_category_name varchar(255),
	standard_category varchar(100),
	standard_sub_category varchar(100),
	category_confidence numeric(4, 3),
	collected_at timestamptz not null,
	last_seen_at timestamptz not null,
	availability_checked_at timestamptz,
	constraint uk_product_site_source unique (site_id, source_product_id)
);

create table product_measurement (
	id bigserial primary key,
	product_id bigint not null references product(id) on delete cascade,
	part varchar(50) not null,
	value_cm numeric(8, 2) not null,
	raw_text text,
	confidence numeric(4, 3) not null,
	source varchar(50) not null,
	updated_at timestamptz not null
);

create index idx_product_collected_at on product(collected_at desc);
create index idx_product_stock_status on product(stock_status);
create index idx_product_measurement_part_value on product_measurement(part, value_cm);

insert into crawl_site (
	code,
	display_name,
	base_url,
	platform,
	crawl_interval_minutes,
	crawler_status,
	consecutive_no_change_count
) values (
	'rocketsalad',
	'로켓샐러드',
	'https://www.rocketsalad.co.kr',
	'MakeShop',
	60,
	'ACTIVE',
	0
) on conflict (code) do nothing;
