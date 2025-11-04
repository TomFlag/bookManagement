# BookManagement

## 概要

コーディングテスト用リポジトリ

## ブランチ戦略

* main - 完成ソースの提出用
* develop - 開発中の最新ソース
* dev-XXX - 各機能の作成・改修用。developから作成してdevelopにマージ

## ディレクトリ構成

## 起動手順

1. JDK 21以上をインストール
2. Mavenをインストール
3. リポジトリをクローン
4. ターミナルでプロジェクトルートに移動
5. 以下コマンドを実行してDB起動（flywayマイグレーションが自動実行）

   ```bash
   docker compose up -d
   ```
6. DBの起動確認 ヘルスチェックが実装済みのため、status: healthyになればOK

   ```bash
   docker compose ps
   ```
7. 以下コマンドを実行してアプリケーション起動

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
  * 著者は名前と生年月日で一意に特定する
  * 同姓同名の著者が存在する場合があるため、名前のみでの特定は行わない
  * 名前と生年月日がいずれも同じ著者は存在しないものとする
