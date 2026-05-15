update product p
set standard_category = '아우터',
	standard_sub_category = '자켓',
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and p.standard_category is null
  and (
	  lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%jacket%'
	  or lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%blazer%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%자켓%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%재킷%'
  );

update product p
set standard_category = '아우터',
	standard_sub_category = '코트',
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and p.standard_category is null
  and (
	  lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%coat%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%코트%'
  );

update product p
set standard_category = '하의',
	standard_sub_category = '데님',
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and p.standard_category is null
  and (
	  lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%jeans%'
	  or lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%denim%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%데님%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%청바지%'
  );

update product p
set standard_category = '하의',
	standard_sub_category = '팬츠',
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and p.standard_category is null
  and (
	  lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%pants%'
	  or lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%pant%'
	  or lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%trouser%'
	  or lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%shorts%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%팬츠%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%바지%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%쇼츠%'
  );

update product p
set standard_category = '하의',
	standard_sub_category = '스커트',
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and p.standard_category is null
  and (
	  lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%skirt%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%스커트%'
  );

update product p
set standard_category = '상의',
	standard_sub_category = '니트',
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and p.standard_category is null
  and (
	  lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%knit%'
	  or lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%sweater%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%니트%'
  );

update product p
set standard_category = '상의',
	standard_sub_category = '스웻',
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and p.standard_category is null
  and (
	  lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%sweat%'
	  or lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%sweatshirt%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%스웻%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%스웨트%'
  );

update product p
set standard_category = '상의',
	standard_sub_category = '셔츠',
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and p.standard_category is null
  and (
	  lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%shirt%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%셔츠%'
  );

update product p
set standard_category = '액세서리',
	standard_sub_category = '모자',
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and p.standard_category is null
  and (
	  lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%cap%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%모자%'
  );

update product p
set standard_category = '액세서리',
	standard_sub_category = '벨트',
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and p.standard_category is null
  and (
	  lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%belt%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%벨트%'
  );

update product p
set standard_category = '신발',
	standard_sub_category = null,
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and p.standard_category is null
  and (
	  lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%shoes%'
	  or lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%shoe%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%슈즈%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%신발%'
  );

update product p
set standard_category = '가방',
	standard_sub_category = null,
	category_confidence = 0.800
from crawl_site s
where p.site_id = s.id
  and s.code = 'rocketsalad'
  and p.standard_category is null
  and (
	  lower(coalesce(p.name, '') || ' ' || coalesce(p.description, '')) like '%bag%'
	  or coalesce(p.name, '') || ' ' || coalesce(p.description, '') like '%가방%'
  );
