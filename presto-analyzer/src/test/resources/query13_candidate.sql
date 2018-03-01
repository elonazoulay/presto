-- atn3_adhoc2
-- prism
-- bi
-- unidash:550844698625528:argus:495387
-- 20180225_192843_46378_fswt3
WITH base AS

    (SELECT
        a.ds,
        COALESCE(cl.country, 'other') AS country,
        a.aggregation_level,
        a.interface,
        a.lifestage_prediction,
     		'actual' AS revision_date,
        SUM(a.mv) AS mv,
     		0 AS mvl,
     		0 AS mvu
    FROM
        (SELECT
            ds,
            country,
            aggregation_level,
            interface,
            IF(lifestage_prediction='','overall',REGEXP_REPLACE(COALESCE(lifestage_prediction,'unknown'),'early_|late_')) AS lifestage_prediction,

         				SUM(unique_user_count) AS mv

        FROM
            core_metrics_summary
        WHERE
         		lifestage_prediction IS NOT NULL
            AND aggregation_level IN ('interface')
            AND metric_name = 'map'
        		AND interface IN ('overall_fb_messenger')
         		AND ds BETWEEN '2016-01-01' AND '2018-12-31'

        GROUP BY
            1,2,3,4,5) a
        LEFT OUTER JOIN
        (SELECT
            country
        FROM
            messenger_metrics_forecast_country_lists
        WHERE
            aggregation_level IN ('interface')
        		AND metric_name = 'map'

         				AND revision_date IN ('2018-01-10')

        GROUP BY
        		1) cl
        ON
            a.country = cl.country

    GROUP BY
        1,2,3,4,5,6)
,



agg AS
(SELECT
		c.ds,
    -- Grouping Variable

				IF(c.country = 'other', 'bottom_5pct', COALESCE(gr.group_var, 'oth'))
     AS group_var,

 				SUM(c.mv) AS mv,
 				SUM(c.mvl) AS mvl,
 				SUM(c.mvu) AS mvu

FROM
		base c

        LEFT OUTER JOIN
        (SELECT
            country_abbr,

         				COALESCE(geo_name, 'not-mapped') AS group_var

        FROM
            dim_geo_country_region
        WHERE
            ds = '2018-02-25'
        		) gr
        ON
            c.country = gr.country_abbr


GROUP BY
		1,2)

SELECT
		-- ds and group

    		ds,
    		CAST(DENSE_RANK() OVER(ORDER BY group_var) AS VARCHAR) ||'. '|| group_var AS group_var,


		-- metric value

 				mv


    		AS ""

FROM
    agg

ORDER BY
		group_var,
    ds