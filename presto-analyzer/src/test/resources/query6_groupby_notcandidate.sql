SELECT b.ds AS dateid, s.pack_id, s.name AS pack_name, b.sticker_id,
    i.uri AS sticker_image, SUM(b.senders) AS senders
FROM stickers_server_sends_by_id b
INNER JOIN stickers_info_latest i
    ON b.sticker_id=i.sticker_id
INNER JOIN sticker_packs_info_latest s
    ON i.pack_id=s.pack_id
AND b.ds =  '2018-03-02'
GROUP BY b.ds, s.pack_id, s.name, b.sticker_id, i.uri
HAVING SUM(b.senders) >= 0
ORDER BY b.ds desc, senders DESC
LIMIT 1000