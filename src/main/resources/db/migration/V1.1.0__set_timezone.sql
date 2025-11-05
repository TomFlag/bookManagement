-- DBのタイムゾーンをAsia/Tokyoに設定し、関連するカラムや制約を変更

BEGIN;

-- 既存チェックを削除
ALTER TABLE authors
  DROP CONSTRAINT IF EXISTS ck_authors_birth_date;

-- date -> timestamptz に変換（既存の DATE を Asia/Tokyo の 00:00 として解釈）
ALTER TABLE authors
  ALTER COLUMN birth_date TYPE timestamptz
  USING ((birth_date::timestamp) AT TIME ZONE 'Asia/Tokyo');

-- チェック制約を Asia/Tokyo の日付で比較するように再作成
ALTER TABLE authors
  ADD CONSTRAINT ck_authors_birth_date
  CHECK (
    ((birth_date AT TIME ZONE 'Asia/Tokyo')::date) < ((now() AT TIME ZONE 'Asia/Tokyo')::date)
  );

COMMIT;
