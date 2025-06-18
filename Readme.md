# Personal Financial Assistant(仮)

## Dev Containerで開発を始める

### 事前準備 (必須インストール)

* **Git**
* **Docker Desktop**
* **VS Code**
* **VS Code 拡張機能: Dev Containers**

### 開発環境セットアップ手順

1.  **リポジトリをクローン**

    ```bash
    git clone https://github.com/tignear/personal-financial-assistant.git
    cd personal-financial-assistant
    ```

2.  **VS CodeでDev Containerを開く**

    * VS Codeでプロジェクトのルートフォルダを開きます。
    * 表示されるポップアップの「**コンテナーで再度開く**」をクリックするか、
    * コマンドパレット (Ctrl+Shift+P または Cmd+Shift+P) で「`Dev Containers: Reopen in Container`」を実行します。

3.  **自動ビルドと起動完了を待つ**

    * 初回のみ数分かかる場合があります。
    * VS Code左下の表示が「**Dev Container: Scala**」になれば完了です。

### 開発開始

#### DBの起動とマイグレーション

1. DB(PostgreSQL)の起動

    ```bash
    docker compose up -d
    # DB: localhost:55432, ユーザー: pfa_user, パスワード: pfa_pass, DB名: pfa_db
    # DB: localhost:55532, ユーザー: pfa_user, パスワード: pfa_pass, DB名: pfa_test_db
    ```

2. DBマイグレーション（テーブル作成）

    ```bash
    sbt Test/flywayMigrate
    sbt flywayMigrate
    # eventテーブルが作成される
    ```

#### インストール済みツールと機能

* **Scala/Java開発環境**:
    * Java 24 (GraalVM/Temurin) および **SBT (Scala Build Tool)** がインストールされています。
    * コンテナ起動時に自動でプロジェクトがビルドされます。
* **SBTの操作**:
    * ターミナルで `sbt` と入力して起動します。
    * SBT内で `run` を実行すると、**ポート8080**でサーバーが起動し、コンテナ外からアクセス可能です。
    * `GraalVMNativeImage/packageBin` でネイティブ実行ファイルを作成できます。
* **VS Code統合機能**:
    * Scalaの言語サーバー「**Metals**」とJava拡張機能が自動インストールされます。
    * Metalsによるデバッグが可能です。もし動かない場合は「**Metals: Import build**」を試してください。
* **Docker操作**:
    * コンテナ内でDockerコマンドを使えます。

### トラブルシューティング

* **Docker Desktopが起動している**ことを確認してください。
* **VS CodeのDev Containers拡張機能がインストールされている**ことを確認してください。
* VS Codeの「出力」パネル (Log (Dev Containers)) で**エラーの詳細を確認**してください。