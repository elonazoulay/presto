-- carbon
-- prism
-- payments
-- unidash:977119055754577:argus:407592
-- 20180305_181114_61594_22hm2
SELECT
        DISTINCT LOWER(if(tier_type IS NULL,'n/a',tier_type)) AS tier_type
FROM ep_new_pay_prod_events
WHERE ds = '2018-03-04'
        AND event_date >= '2017-09-05'
ORDER BY tier_type ASC