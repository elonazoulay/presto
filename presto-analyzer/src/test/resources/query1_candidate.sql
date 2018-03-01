SELECT
  pop.country,
  c2r.geo_name as region,
  sum(pop.population_forecast) as pop,
  sum(pop.internet_forecast) as internet,
  sum(fam.fam_map) as family_map,
  sum(fam.feed_map) as feed_map,
  sum(fam.messaging_map) as messaging_map,
  sum(fam.fb_map) as fb_map,
  sum(fam.fb_blue_map) as fb_blue_map,
  sum(fam.msgr_map) as msgr_map,
  sum(fam.wa_map) as wa_map,
  sum(fam.ig_map) as ig_map
  -- sum(fcst.wa_not_fb_fcst) as wa_not_fb_map,
  -- sum(fcst.ig_not_fb_not_wa_fcst) as ig_not_fb_not_wa
FROM -- internet and population
  (
  SELECT * FROM fcst_growth:bi WHERE revision_date = '2017-08-15' AND ds = '2017-12-30'
  ) pop

JOIN ( -- family stats
  SELECT country,
  sum(if(app = 'facebook_family', map_30, 0)) as fam_map,
  sum(if(app = 'feed', map_30, 0)) as feed_map,
  sum(if(app = 'messaging', map_30, 0)) as messaging_map,
  sum(if(app = 'fb', map_30, 0)) as fb_map,
  sum(if(app = 'fb_blue', map_30, 0)) as fb_blue_map,
  sum(if(app = 'msgr', map_30, 0)) as msgr_map,
  sum(if(app = 'wa', map_30, 0)) as wa_map,
  sum(if(app = 'ig', map_30, 0)) as ig_map
FROM facebook_family_user_summary
WHERE ds = '2017-12-30'
  AND aggregation_level = 'country'
GROUP BY 1
ORDER BY 2 DESC
  ) fam
  ON pop.country = fam.country

JOIN ( -- region
    select distinct
        country_abbr,
        geo_name
    from geo_country_earnings_call:bi
  ) c2r
  ON fam.country = c2r.country_abbr

GROUP BY 1, 2
ORDER BY sum(pop.population_forecast) DESC
