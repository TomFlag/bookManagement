# BookManagement

## 概要

コーディングテスト用リポジトリ

## ブランチ戦略

* main - 完成ソースの提出用
* develop - 開発中の最新ソース
* dev-XXX - 各機能の作成・改修用。developから作成してdevelopにマージ

## 動作確認済み環境

* macOS
* Docker Desktop
* openjdk 21.0.5

## 起動手順

1. 上記の必要なツールをインストール
2. リポジトリをクローン
3. ターミナルでプロジェクトルートに移動
4. 以下コマンドを実行してDB起動（flywayマイグレーションがコンテナ利用して自動実行）

   ```bash
   docker compose up -d
   ```

5. DBの起動確認
   ヘルスチェックが実装済みのため、status: healthyになればOK

   ```bash
   docker compose ps
   ```

6. DBマイグレーションの確認

   1.2.0までマイグレーションされていることを確認

   ```bash
   docker compose -f compose.yaml exec postgres psql -U appuser -d appdb -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
   ```

7. 以下コマンドを実行して起動、もしくはIntellijから起動 (jooqは自動実行される)

   ```bash
   ./gradlew bootRun
   ```

## コーディングルール

以下ルールに従って設計・実装

* 日付時刻とタイムゾーン
  * DBはAsia/Tokyoのtimestamp型
  * Spring Bootの設定もAsia/Tokyoだが、時刻は扱わない項目はLocalDateで対応
  * 仮に他のタイムゾーンを利用したい場合は、Spring Bootの設定を変更して対応する

## 設計

### URL設計

URLはリソースを示し、操作はHTTPメソッドで表現.

| メソッド | URL                           | 概要             |
|------|-------------------------------|----------------|
| POST | /api/books                    | 書籍登録           |
| PUT  | /api/books/{bookId}           | 書籍更新           |
| GET  | /api/authors/{authorId}/books | 著者に紐付く書籍の一覧を取得 |
| POST | /authors                      | 著者登録           |
| PUT  | /authors/{authorId}           | 著者更新           |

### DB設計

詳細は[Migrationスクリプト](src/main/resources/db/migration)を参照。

#### テーブル構成

* authors (著者情報)
* books (書籍情報)
* book_authors (書籍と著者の多対多リレーション)

#### authorsテーブルの行特定

* 著者は名前と生年月日の組み合わせで一意に特定する
* 名前と生年月日がいずれも同じ著者は存在しないものとする

## curl 実行例

ローカルでアプリケーションが起動している前提（デフォルト: `http://localhost:8080`）でのリクエスト例

複数回実行すると重複登録等エラーになる可能性あり

変数設定:

```bash
BASE_URL="http://localhost:8080"
```

1) 著者を作成する — POST /api/authors

   作成結果から id を取り出して変数に保存:

   ```bash
   AUTHOR_ID=$(curl -s -X POST "$BASE_URL/api/authors" \
      -H "Content-Type: application/json" \
      -d '{"name":"Haruki Murakami","birthDate":"1949-01-01"}' | jq -r '.id')
   echo "created author id: $AUTHOR_ID"
   ```

2) 著者を更新する — PUT /api/authors/{id}

   変更したいフィールドだけ指定（null は変更無し）

   ```bash
   curl -s -X PUT "$BASE_URL/api/authors/$AUTHOR_ID" \
      -H "Content-Type: application/json" \
      -d '{"newName":"H. Murakami"}'
   ```

3) 書籍を作成して id を取得 — POST /api/books

    ```bash
    payload=$(jq -n --arg title "Kotlin in Action" --arg status "PUBLISHED" \
       --argjson authorIds "[${AUTHOR_ID}]" --argjson price 2500.00 \
       '{title:$title, authorIds:$authorIds, price:$price, status:$status}')

    BOOK_ID=$(curl -s -X POST "$BASE_URL/api/books" \
       -H "Content-Type: application/json" \
       -d "$payload" | jq -r '.id')

    echo "created book id: $BOOK_ID"
    ```

4) 書籍を更新 — PUT /api/books/{id}

   ```bash
   curl -s -X PUT "$BASE_URL/api/books/$BOOK_ID" \
      -H "Content-Type: application/json" \
      -d '{"title":"Kotlin in Action - 2nd","price":2200.00}'
   ```

5) 著者に紐づく書籍一覧を取得 — GET /api/authors/{id}/books

   ```bash
   curl -s "$BASE_URL/api/authors/$AUTHOR_ID/books"
   ```
