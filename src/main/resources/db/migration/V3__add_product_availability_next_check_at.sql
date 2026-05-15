alter table product
	add column availability_next_check_at timestamptz;

update product
set availability_next_check_at = coalesce(availability_checked_at, collected_at);

create index idx_product_availability_next_check_at on product(availability_next_check_at);
