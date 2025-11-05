-- Flyway Migration V1.0.0
-- 再実行耐性を考慮

-- 出版状況を表す列挙型の作成
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'publication_status') THEN
    CREATE TYPE publication_status AS ENUM ('UNPUBLISHED', 'PUBLISHED');
  END IF;
END;
$$;

-- 著者テーブル
CREATE TABLE IF NOT EXISTS authors (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name TEXT NOT NULL,
  birth_date DATE NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_authors_birth_date CHECK (birth_date < CURRENT_DATE)
);

-- 書籍テーブル
CREATE TABLE IF NOT EXISTS books (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  title TEXT NOT NULL,
  price NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
  status publication_status NOT NULL DEFAULT 'UNPUBLISHED',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 書籍-著者関連テーブル (多対多)
CREATE TABLE IF NOT EXISTS book_authors (
  book_id BIGINT NOT NULL,
  author_id BIGINT NOT NULL,
  author_order INT NOT NULL DEFAULT 1,
  PRIMARY KEY (book_id, author_id),
  FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
  FOREIGN KEY (author_id) REFERENCES authors(id) ON DELETE RESTRICT
);

-- 書籍の出版状態変更制限トリガー: PUBLISHED -> UNPUBLISHED は不可
CREATE OR REPLACE FUNCTION fn_prevent_unpublish() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'UPDATE' AND OLD.status = 'PUBLISHED'::publication_status AND NEW.status = 'UNPUBLISHED'::publication_status THEN
    RAISE EXCEPTION 'Cannot change status from PUBLISHED to UNPUBLISHED for book id=%', OLD.id;
  END IF;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_prevent_unpublish ON books;
CREATE TRIGGER trg_prevent_unpublish
  BEFORE UPDATE ON books
  FOR EACH ROW
  EXECUTE FUNCTION fn_prevent_unpublish();

-- 書籍に少なくとも一人の著者がいることを保証するトリガー
CREATE OR REPLACE FUNCTION fn_check_books_have_authors() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  bad_ids TEXT;
BEGIN
  SELECT string_agg(b.id::text, ',') INTO bad_ids
  FROM books b
  LEFT JOIN book_authors ba ON ba.book_id = b.id
  GROUP BY b.id
  HAVING count(ba.author_id) = 0;

  IF bad_ids IS NOT NULL THEN
    RAISE EXCEPTION 'Books with no authors detected (ids=%). Every book must have at least one author.', bad_ids;
  END IF;

  RETURN NULL;
END;
$$;
DROP TRIGGER IF EXISTS trg_ensure_books_have_authors ON book_authors;
CREATE CONSTRAINT TRIGGER trg_ensure_books_have_authors
  AFTER INSERT OR UPDATE OR DELETE ON book_authors
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW
  EXECUTE FUNCTION fn_check_books_have_authors();

DROP TRIGGER IF EXISTS trg_ensure_books_have_authors_on_books ON books;
CREATE CONSTRAINT TRIGGER trg_ensure_books_have_authors_on_books
  AFTER INSERT OR UPDATE OR DELETE ON books
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW
  EXECUTE FUNCTION fn_check_books_have_authors();

-- updated_at 自動更新トリガー
CREATE OR REPLACE FUNCTION fn_set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_set_updated_at_authors ON authors;
CREATE TRIGGER trg_set_updated_at_authors
  BEFORE UPDATE ON authors
  FOR EACH ROW
  EXECUTE FUNCTION fn_set_updated_at();
DROP TRIGGER IF EXISTS trg_set_updated_at_books ON books;
CREATE TRIGGER trg_set_updated_at_books
  BEFORE UPDATE ON books
  FOR EACH ROW
  EXECUTE FUNCTION fn_set_updated_at();