update product p
set standard_category = '아우터',
	standard_sub_category = null,
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and p.standard_category is null
  and (
	  lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%parka%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%파카%'
  );

update product p
set standard_category = '상의',
	standard_sub_category = '베스트',
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and p.standard_category is null
  and (
	  lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%vest%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%베스트%'
  );

update product p
set standard_category = '상의',
	standard_sub_category = '티셔츠',
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and p.standard_category is null
  and (
	  lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%t-shirt%'
	  or lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%t shirt%'
	  or lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%print t%'
	  or lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '% t for%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%티셔츠%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%티셔트%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%프린트 티%'
  );
