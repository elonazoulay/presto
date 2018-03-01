-- de1atn3
-- raptor
-- de
-- unidash:1937407619825193:argus:396947
-- 20180227_204146_22234_h4in6
SELECT


      COALESCE(recall.teen_nonteen, precision.teen_nonteen) AS teen_nonteen,
      recall.teen_nonteen_pr AS recall,
      precision.teen_nonteen_pr AS precision



FROM (
  SELECT
  	*

  FROM
    fct_age_affinity_pr_dash
  WHERE
    prediction_source = 'age_affinity_prod_2018-02-17'
    AND truth_source = 'CO_LABELED_TEST_SET'
    AND ds = CAST('2018-02-24' AS DATE)
    AND precision_recall = 'RECALL'
    AND age IS NULL
    AND new_user IS NULL
    AND friend_count_bucket IS NULL
    AND country = 'US'


      AND teen_nonteen IS NOT NULL


) recall
FULL JOIN (
  SELECT
  	*

  FROM
    fct_age_affinity_pr_dash
  WHERE
    prediction_source = 'age_affinity_prod_2018-02-17'
    AND truth_source = 'CO_LABELED_TEST_SET'
    AND ds = CAST('2018-02-24' AS DATE)
    AND precision_recall = 'PRECISION'
    AND age IS NULL
    AND new_user IS NULL
    AND friend_count_bucket IS NULL
    AND country = 'US'


      AND teen_nonteen IS NOT NULL


) precision


ON
	recall.teen_nonteen = precision.teen_nonteen
ORDER BY COALESCE(recall.teen_nonteen, precision.teen_nonteen)
