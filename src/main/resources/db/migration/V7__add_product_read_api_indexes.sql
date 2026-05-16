create index idx_product_site_latest on product(site_id, collected_at desc, id desc);

create index idx_product_category_latest on product(standard_category, standard_sub_category, collected_at desc, id desc);

create index idx_product_stock_latest on product(stock_status, collected_at desc, id desc);

create index idx_product_display_price_latest on product((coalesce(sale_price, original_price)), collected_at desc, id desc);

create index idx_product_measurement_part_value_product on product_measurement(part, value_cm, product_id);

create index idx_product_measurement_product_id_id on product_measurement(product_id, id);

create extension if not exists pg_trgm;

create index idx_product_name_trgm on product using gin (lower(name) gin_trgm_ops);
