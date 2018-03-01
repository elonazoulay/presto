-- ftw2_adhoc2
-- prism
-- ad_metrics
-- unidash:176196899468288:argus:348307
-- 20180228_104756_43382_gyg84
SELECT
distinct lower(site) as site
FROM
(
        SELECT
     IF(CARDINALITY(split(regexp_replace(URL_EXTRACT_PATH(uri), '/*$'),'/'))>=4,split(split(regexp_replace(URL_EXTRACT_PATH(uri), '/*$'),'/')[4],'_')[1],NULL) as site
    FROM
    fb4b_1_impressions_extended
    WHERE
    traffic_type  = 'People'
    AND section IN ('e','m')
    AND ds BETWEEN '2018-01-13' AND '2018-02-27'
        GROUP BY 1

        --      UNION ALL

  -- SELECT
  --   split(split(regexp_replace(URL_EXTRACT_PATH(uri), '/*$'),'/')[4],'_')[1] as site
  -- FROM  fb4b_1_impressions_extended_anon
  -- WHERE
  --  traffic_type  = 'People'
  --    AND section IN ('e','m')

  -- GROUP BY
  --  1
) a
ORDER BY site ASC