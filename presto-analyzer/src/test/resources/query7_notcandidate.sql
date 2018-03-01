-- atn3
-- prism
-- growth
-- unidash:519844961498968:argus:273938
-- 20180305_102842_90797_hxuic
SELECT
  CONCAT(chk.ds, CONCAT(' ', chk.algo)) as ds_algo,
  chk.locale,
  chk.native_string_hash,
  --
  chk.parent_hash,
  chk.priority,
  chk.priority_rank,
  --
  s.string_text,
  s.suffix_text,
  --
  s.total_votes,
  --
  s.cur_approval_type AS cur_status,
  s.new_action,
  IF(s.is_cur_same_as_cand, 'SAME', 'DIFF')
    AS same,
  s.cand_cred_level,
  s.cur_trans_text,
  s.cand_trans_text,
  --
  s.cur_votes,
  s.cur_cred,
  --
  s.cand_votes,
  s.cand_cred,
  --
  ROUND(s.cur_risk, 0) AS cur_risk,
  ROUND(s.cand_risk, 0) AS cand_risk,
  --
  ROUND(s.new_risk, 0) AS new_risk,
  --
  s.samples,
  s.users,
  ROUND(s.est_users,0) AS est_users,
  ROUND(s.est_imps_per_user, 1) AS est_imps_per_user,
  ROUND(s.string_impact,0) AS string_impact,
  s.impact_metric,
  s.null_id_samples,
  s.zero_id_samples,
  s.log_user_imps_metric,
  --
  CONCAT('https://our.intern.facebook.com/intern/argus/view/',
    CONCAT('266366?bind_vars[ds]=',
      CONCAT(chk.ds,
        CONCAT('&bind_vars[algo]=',
          CONCAT(chk.algo,
            CONCAT('&bind_vars[locale]=',
              CONCAT(chk.locale,
                CONCAT('&bind_vars[native_string_hash]=',
                  chk.native_string_hash
  )))))))) AS translations,
  --
  CONCAT('https://our.intern.facebook.com/intern/argus/view/',
    CONCAT('266371?bind_vars[ds]=',
      CONCAT(chk.ds,
        CONCAT('&bind_vars[algo]=',
          CONCAT(chk.algo,
            CONCAT('&bind_vars[locale]=',
              CONCAT(chk.locale,
                CONCAT('&bind_vars[native_string_hash]=',
                  chk.native_string_hash
  )))))))) AS votes,
  --
  s.cur_tid,
  s.cur_translator_id,
  s.cur_translator_type,
  s.imm_preview,
  --
  cand_translator_id,
  cand_translator_type,
  --
  new_uses_cand,
  new_uses_cur,
  --
  count_total_actions
FROM (
  SELECT
    *
  FROM
    intl_cred_transapp_strings
WHERE
  (('LATEST' = 'LATEST' AND ds = '2018-03-04')
  OR (ds = 'LATEST')
  OR ('LATEST' = 'DATEID' AND ds = '2018-03-04')
  OR ('LATEST' = 'DATEID-1' AND ds = '2018-03-03')
    )
 AND algo = 'alpha87'
 AND status_algo = 'req2'
 AND impact_algo_and_subset = 'combined-all'
 AND impact_metric = 'reach_log_freq_plus_znids'
 AND (locale = 'en_US' OR 'en_US' = 'fb_LL'
    OR 'en_US' = 'en_US' OR 'en_US' = '')
 AND priority_rank <= 2000
    AND is_public = 1
    -- include checkers and NON admin_approved strings
    AND (is_checker = 1 OR is_admin_approved = 0)
) chk
LEFT OUTER JOIN (
  SELECT
    *
  FROM
  intl_cred_string_dash
WHERE
  (('LATEST' = 'LATEST' AND ds = '2018-03-04')
  OR (ds = 'LATEST')
  OR ('LATEST' = 'DATEID' AND ds = '2018-03-04')
  OR ('LATEST' = 'DATEID-1' AND ds = '2018-03-03')
    )
 AND algo = 'alpha87'
 AND status_algo = 'req2'
 AND impact_algo_and_subset = 'combined-all'
 AND impact_metric = 'reach_log_freq_plus_znids'
 AND (locale = 'en_US' OR 'en_US' = 'fb_LL'
    OR 'en_US' = 'en_US' OR 'en_US' = '')
) s
ON
  s.locale = chk.locale
  AND s.native_string_hash = chk.native_string_hash
ORDER BY
  COALESCE(chk.priority, 0) DESC, est_users DESC
LIMIT 2000

