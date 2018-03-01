presto --output-format TSV --execute "select environment, catalog, schema, query_id, source, query from nectar_presto_query_stats_r where upper(query) like '%ORDER BY%' and ds >= '2018-02-23' and environment not like '%batch%' and event_type = 'QueryCompletion' and query_state = 'FINISHED' and upper(query) not like '%INSERT INTO%' and source like '%argus%' and source not like '%verifier%'" di 
