-- Authorsテーブルにname,birth_dateの組み合わせに対して一意制約を追加

BEGIN;

-- 1) 同じ (name, birth_date) のグループについて残すべき author id（最小 id）を決定し、
--    他の行の参照をその keeper_id に更新する
WITH author_keep AS (
  SELECT
    id,
    name,
    birth_date,
    MIN(id) OVER (PARTITION BY name, birth_date) AS keeper_id
  FROM authors
)
UPDATE book_authors ba
SET author_id = ak.keeper_id
FROM author_keep ak
WHERE ba.author_id = ak.id
  AND ak.id <> ak.keeper_id;

-- 2) book_authors 内で (book_id, author_id) の重複行が生じうるので重複を削除する
DELETE FROM book_authors ba
USING (
  SELECT book_id, author_id, MIN(ctid) AS keep_ctid
  FROM book_authors
  GROUP BY book_id, author_id
  HAVING COUNT(*) > 1
) dups
WHERE ba.book_id = dups.book_id
  AND ba.author_id = dups.author_id
  AND ba.ctid <> dups.keep_ctid;

-- 3) authors テーブルの重複行（row_number > 1）を削除する（keeper_id を残す）
WITH to_delete AS (
  SELECT id FROM (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY name, birth_date ORDER BY id) AS rn
    FROM authors
  ) t
  WHERE rn > 1
)
DELETE FROM authors a
USING to_delete td
WHERE a.id = td.id;

-- 4) 一意制約を追加
ALTER TABLE authors
  ADD CONSTRAINT uq_authors_name_birth_date UNIQUE (name, birth_date);

COMMIT;