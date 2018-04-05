presto --smc presto_tools --output-format TSV --execute "
select  environment, catalog, schema, max_by(query_id, end_time) as query_id,
source, max_by(query, end_time) as query
from presto_queries where end_time between cast('2018-03-01 00:00:00' as timestamp) and cast('2018-04-01 00:00:00' as timestamp) and lower(query) like '%join%using%(%)%' and status = 'FINISHED'
and source not like 'verifier%'
and source not like '%@test@%'
and source not like '%@%backfill%'
 group by environment, catalog, schema, source" > queries.all