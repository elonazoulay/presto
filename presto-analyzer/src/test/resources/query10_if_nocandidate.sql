-- atn3_adhoc2
-- prism
-- cea
-- unidash:690207337772696:argus:243835
-- 20180303_204345_86563_u22ae
select ds, channel, region,
  IF(channel='www' AND ds<'2015-01-01', peak_alm*0.716, peak_alm) as peak_alm
from cea_alm_region_channel_local_peak
where ds >='2016-03-02'
and channel in ('www','mobile')
and region != 'ROW'
and region is not null
order by ds desc, peak_alm desc