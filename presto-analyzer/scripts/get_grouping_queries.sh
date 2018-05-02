presto --smc presto_tools
ptools --output-format TSV --execute "
select  environment, catalog, schema, max_by(query_id, end_time) as query_id,
source, max_by(query, end_time) as query
from presto_queries where end_time between cast('2018-04-01 00:00:00' as timestamp) and
cast('2018-05-01 00:00:00' as timestamp) and upper(query) like '%GROUP BY%' and
(upper(query) like '%GROUPING SETS%' or upper(query) like '%CUBE%' or upper(query) like '%ROLLUP%') and
(status = 'FINISHED' or (status = 'FAILED' and error_code = 'SYNTAX_ERROR'))
and source not like 'verifier%'
 group by environment, catalog, schema, source" > queries.all