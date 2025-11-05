# BookManagement

## 概要

コーディングテスト用リポジトリ

## ブランチ戦略

* main - 完成ソースの提出用
* develop - 開発中の最新ソース
* dev-XXX - 各機能の作成・改修用。developから作成してdevelopにマージ

## 起動手順

1. JDK 21以上をインストール
2. リポジトリをクローン
3. ターミナルでプロジェクトルートに移動
4. 以下コマンドを実行してDB起動（flywayマイグレーションが自動実行）

   ```bash
   docker compose up -d
   ```

5. DBの起動確認 ヘルスチェックが実装済みのため、status: healthyになればOK

   ```bash
   docker compose ps
   ```

6. 以下コマンドを実行してアプリケーション起動

   ```bash
   ./gradlew bootRun
   ```

## コーディングルール

以下ルールに従って設計・実装

* 日付時刻とタイムゾーン
  * DBはAsia/Tokyoのtimestamp型
  * Spring Bootの設定もAsia/Tokyoだが、時刻は扱わない項目はLocalDateで対応
  * 仮に他のタイムゾーンを利用したい場合は、Spring Bootの設定を変更して対応する
* 著者の特定
  * 著者は名前と生年月日の組み合わせで一意に特定する
  * 名前と生年月日がいずれも同じ著者は存在しないものとする

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

#### テーブル構成

* authors (著者情報)
* books (書籍情報)
* book_authors (書籍と著者の多対多リレーション)

詳細は[Migrationスクリプト](src/main/resources/db/migration/)を参照。
