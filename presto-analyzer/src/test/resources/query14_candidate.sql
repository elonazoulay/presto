-- boron
-- prism
-- platform
-- unidash:2237949063096010:argus:403512
-- 20180226_153112_51531_f77nq
select concat(date, ' ', name) as Date_ProgramName, CASE
WHEN loyalty_event = 'create' THEN '(1) join'
WHEN loyalty_event = 'add_point' THEN '(2) add_point'
WHEN loyalty_event = 'redeem' THEN '(3) redeem'
ELSE loyalty_event
END as loyalty_event,
count_today as count,
if (count_yesterday= 0, '--', concat(cast(round((count_today - count_yesterday) * 100.0 / count_yesterday, 2) as varchar(10)), '%')) as DoD,
if (count_last_week= 0, '--', concat(cast(round((count_today - count_last_week) * 100.0 / count_last_week, 2) as varchar(10)), '%')) as WoW

from (

select date, name, loyalty_event,
 count_if(ds = '2018-02-25') count_today,
 count_if(ds = '2018-02-24') count_yesterday,
 count_if(ds = '2018-02-18') count_last_week from (

SELECT '2018-02-25' AS date,
     if (loyalty_event_type = 'purchase', 'add_point', loyalty_event_type) as loyalty_event, l.*,
     CASE WHEN p.page_language = 'en' THEN d.name
            ELSE concat('[', p.primary_alias, '] ', d.name) END as name
FROM dim_page:di p
     join loyalty_events l
     on  p.fbid = l.page_fbids[1]
    and p.ds = '2018-02-25' and
	(l.ds = '2018-02-25' or l.ds = '2018-02-24' or l.ds = '2018-02-18')
   AND cast(loyalty_program_fbid as varchar(128)) not in ('1998565523707776','1875698389414682','1060779620732228','1893556960873828','728607603977209','298791993877011','706844886188943','1808382499449107','416313432061884','950931938372566','1954779938067319','306110276505920','624433377760604','1261970323920960','1349231601811949','141468846416487','766719920141921','379356265765939','375396282842571','1206643149431789','1830380760545344','243323929427385','1849183812007242','1181901425192628','1836783816580508','1869869869923348','1794970400818775','1701497516542030','1243199792443417','696412910541680','1354839771206988','1801037940221524','264263057347969','165226355507876','1644620849180710','1258276610935980','199682793852701','281882535568968','1909022836004331','460134924336356','101654717095738','1746903105601956','428585404157822','308221752942612','1441367669267998','288834204893738')
and loyalty_event_type  in ('purchase','add_point','create','redeem')
and is_user_employee = false
join dim_loyalty_program:di d
    on d.fbid = l.loyalty_program_fbid
    and d.ds = '2018-02-25'
    AND (is_test_program IS NULL or is_test_program = 0)
)
group by date, name, loyalty_event
)

order by date, name, loyalty_event