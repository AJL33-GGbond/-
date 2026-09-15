-- Run against letter_db. Add missing columns only; safe to rerun.
SET time_zone = '+08:00';
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'letters' AND column_name = 'updated_at') = 0,
    'ALTER TABLE letters ADD COLUMN updated_at TIMESTAMP NULL DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'letters' AND column_name = 'sent_at') = 0,
    'ALTER TABLE letters ADD COLUMN sent_at TIMESTAMP NULL DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
UPDATE letters SET updated_at = created_at WHERE updated_at IS NULL;
