SELECT
  MAX(date) date,
  CONCAT(content_actor_type, CONCAT('_', content_type)) funnel_category,
  ROUND(SUM(num_shares)/COUNT(date), 0) num_shares,
  ROUND(SUM(vpvs)/COUNT(date), 0) vpvs,
  ROUND(SUM(outbound_clicks)/COUNT(date), 0) outbound_clicks,
  ROUND(SUM(likes)/COUNT(date), 0) likes,
  ROUND(SUM(comments)/COUNT(date), 0) comments,
  ROUND(SUM(reshares)/COUNT(date), 0) reshares
FROM
(SELECT
  ds as date,
  content_actor_type,
  content_type,
  SUM(num_shares) num_shares,
  SUM(vpvs) vpvs,
  SUM(outbound_clicks) outbound_clicks,
  SUM(likes) likes,
  SUM(comments) comments,
  SUM(reshares) reshares
FROM media1k_domain_link_share_summary
WHERE ds >= '2018-02-14'
AND ds <= '2018-02-28'
AND domain!='youtube.com'
GROUP BY 1,2,3
) A
GROUP BY 2
ORDER BY reshares ASC
