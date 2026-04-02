-- ============================================================
-- 将历史 COS 域名替换为新桶（time-map 上海 -> maptrace 北京）
-- 执行前请备份数据库。
-- 用法示例：
--   mysql -h <主机> -u <用户> -p maptrace < docs/sql/migration-cos-url-replace.sql
-- ============================================================

SET @old := 'https://time-map-1318253552.cos.ap-shanghai.myqcloud.com';
SET @new := 'https://maptrace-1410259056.cos.ap-beijing.myqcloud.com';

-- 预览影响行数（可选，单独执行查看）
-- SELECT COUNT(*) FROM t_photo WHERE image_url LIKE CONCAT('%', @old, '%') OR thumbnail_url LIKE CONCAT('%', @old, '%');
-- SELECT COUNT(*) FROM t_user WHERE avatar_url LIKE CONCAT('%', @old, '%');
-- SELECT COUNT(*) FROM t_cos_delete_record WHERE file_url LIKE CONCAT('%', @old, '%');

UPDATE t_photo
SET
  image_url = REPLACE(image_url, @old, @new),
  thumbnail_url = REPLACE(thumbnail_url, @old, @new)
WHERE image_url LIKE CONCAT('%', @old, '%')
   OR thumbnail_url LIKE CONCAT('%', @old, '%');

UPDATE t_user
SET avatar_url = REPLACE(avatar_url, @old, @new)
WHERE avatar_url LIKE CONCAT('%', @old, '%');

UPDATE t_cos_delete_record
SET file_url = REPLACE(file_url, @old, @new)
WHERE file_url LIKE CONCAT('%', @old, '%');
