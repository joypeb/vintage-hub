update product p
set standard_category = '아우터',
	standard_sub_category = '자켓',
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and lower(p.source_category_name) like 'womancloth%'
  and p.standard_category is null
  and (
	  lower(p.name) like '%jacket%'
	  or lower(p.description) like '%jacket%'
	  or p.name like '%자켓%'
	  or p.description like '%자켓%'
	  or p.name like '%재킷%'
	  or p.description like '%재킷%'
  );

update product p
set standard_category = '아우터',
	standard_sub_category = '코트',
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and lower(p.source_category_name) like 'womancloth%'
  and p.standard_category is null
  and (
	  lower(p.name) like '%coat%'
	  or lower(p.description) like '%coat%'
	  or p.name like '%코트%'
	  or p.description like '%코트%'
  );
