-- de1atn3
-- raptor
-- de
-- unidash:1533470513631023:argus:348860
-- 20180301_072904_09164_h4in6
SELECT
    ds,
    (CASE WHEN user_lifestage_prediction = 'college' THEN 'Adult'
      WHEN user_lifestage_prediction = 'early_high_school' THEN 'Teen'
        WHEN user_lifestage_prediction = 'early_work' THEN 'Adult'
        WHEN user_lifestage_prediction = 'late_high_school' THEN 'Teen'
        WHEN user_lifestage_prediction = 'late_work' THEN 'Adult'
        ELSE 'Unknown' END) AS user_lifestage_prediction,
    avg(SUM(view_time/60))
       over (partition by
             (CASE WHEN user_lifestage_prediction = 'college' THEN 'Adult'
                  WHEN user_lifestage_prediction = 'early_high_school' THEN 'Teen'
                  WHEN user_lifestage_prediction = 'early_work' THEN 'Adult'
                  WHEN user_lifestage_prediction = 'late_high_school' THEN 'Teen'
                  WHEN user_lifestage_prediction = 'late_work' THEN 'Adult'
                  ELSE 'Unknown' END)
             ORDER BY ds asc ROWS between 0 PRECEDING and CURRENT ROW) AS time_spent
FROM fct_video_consumption_summary_dap_new
WHERE video_state_type in ('is_live_streaming')
        AND video_owner_type = (CASE WHEN 'Overall' = 'Overall' THEN 'overall'
                          ELSE UPPER('Overall') END)
    AND post_type = (CASE WHEN 'Overall' = 'Overall' THEN 'overall'
                          ELSE UPPER('Overall') END)
    AND interface_group = 'Overall'
    AND ds >= CAST('2014-03-01' AS DATE)
        AND ds <= CAST('2020-08-04' AS DATE)
                AND player_origin = (CASE WHEN 'newsfeed' = 'Overall' THEN 'overall'
                          ELSE  'newsfeed' END)

GROUP BY
    ds,
  (CASE WHEN user_lifestage_prediction = 'college' THEN 'Adult'
      WHEN user_lifestage_prediction = 'early_high_school' THEN 'Teen'
        WHEN user_lifestage_prediction = 'early_work' THEN 'Adult'
        WHEN user_lifestage_prediction = 'late_high_school' THEN 'Teen'
        WHEN user_lifestage_prediction = 'late_work' THEN 'Adult'
        ELSE 'Unknown' END)
ORDER BY user_lifestage_prediction