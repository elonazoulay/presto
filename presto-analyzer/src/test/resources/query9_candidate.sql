-- boron
-- prism
-- platform
-- unidash:516380961876504:argus:363608
-- 20180227_171652_93823_8pf5d
SELECT
  active.avg active,
  casual.avg casual,
  CAST(round(CAST(active.avg AS DOUBLE) / CAST(casual.avg AS DOUBLE) * 100, 1) AS VARCHAR) || '%' engagement,
  active.grammar
FROM (
  SELECT
    ROUND(AVG(usage)) avg,
    grammar
  FROM (
      select
       ds,
       coalesce(extras['fileGrammar'], 'Null Grammar') grammar,
       count(distinct unixname) usage
      from
       nuclide_analytics_events
      where
       event = 'file-save'
       and ds > '2018-01-27'
       and day_of_week(date_parse(ds, '%Y-%m-%d')) < 6
      group by
       ds,
       coalesce(extras['fileGrammar'], 'Null Grammar')
      having
       coalesce(extras['fileGrammar'], 'Null Grammar') != 'Null Grammar'
  )
  GROUP BY
    grammar
) active
JOIN (
  SELECT
    ROUND(AVG(usage)) avg,
    grammar
  FROM (
      select
       ds,
       grammar,
       count(distinct unixname) usage
      from
       -- nuclide_distinct_arc_project_grammars_by_user is based on any file event (file-%)
       nuclide_distinct_arc_project_grammars_by_user
      where
       ds > '2018-01-27'
       and day_of_week(date_parse(ds, '%Y-%m-%d')) < 6
       and grammar != ''
      group by
       ds,
       grammar
  )
  GROUP BY
    grammar
) casual
ON
  active.grammar = casual.grammar
ORDER BY
  active.avg DESC
