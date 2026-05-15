create table admin_user (
	id bigserial primary key,
	username varchar(100) not null unique,
	password_hash varchar(255) not null,
	enabled boolean not null,
	created_at timestamptz not null,
	updated_at timestamptz not null
);
