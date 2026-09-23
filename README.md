# 推し活管理アプリ saving-favorite

### 概要
色々できる推し活アプリです。<br>
金銭管理に所持グッズ登録できるので、ほんとに色々できます。

### 機能紹介
<details>
<summary>日程管理</summary>
カレンダー機能があるので、推しのライブだったり、推しのグッズの販売開始やコラボ開始時期、終了時期まで登録することができます。
え？標準カレンダーを使えばいいって？それは言わないお約束
</details>
<details>
<summary>金銭管理</summary>
支出を管理できるだけでなく、所持金を設定しておくと、購入予定のグッズも含め、「いくら使ったか」「これを買ったらいくらになるか」を見ることができます。
</details>
<details>
<summary>複数の推しを登録可能</summary>
推しは1人だけじゃない人もいますよね、、、そんなあなた、なんと複数の推しを登録することができます！推し事にグッズの管理等できるから、各推しに使うお金を管理するのが簡単になります。
</details>
<small>そのほかにも様々な機能があります。グッズ登録がめんどくさかったらショップリンクを貼ってみてもいいかもね</small>

### 使用言語・技術
__フロントエンド__<br>
[![My Skills](https://skillicons.dev/icons?i=js,html,css)](https://skillicons.dev)

__バックエンド__<br>
[![My Skills](https://skillicons.dev/icons?i=java&theme=dark)](https://skillicons.dev)

__その他__<br>
<p align="left">
  <a href="https://skillicons.dev">
    <img src="https://skillicons.dev/icons?i=docker" />
  </a>
</p>

拡張機能
[Spring Boot](https://marketplace.visualstudio.com/items?itemName=vmware.vscode-spring-boot)をインストールして実行すると起動します。
ログインしないとアプリは使用できません。

起動方法
[Spring Boot](https://marketplace.visualstudio.com/items?itemName=vmware.vscode-spring-boot)をインストール
どこでも良いので[java拡張子]ファイルを開いて右上の▷をクリック
[localhost:8080](http://localhost:8080/)をwebブラウザに直打ち(このリンクから開いてもいけると思います。)

### 補足と注意
当アプリはURLからの情報取得方法にスクレイピングを使用しております。お使いになるリンク先のページの利用規約を確認した上で使用すること、短スパンでのアクセスはURL先のサーバーに対して高負荷をかける原因になります。また、リンクを使用して保存された画像は保存できないようになっております(スクショ等は未対策)。
アプリとしてリリースする場合、スクレイピング部分において、確実なる安全性・使用想定されるサイト運営からの許諾確認を取ってからの制限等を設けたうえでリリースさせていただきます。

### 商品情報の自動取得について
通常のHTMLだけでなく、公開ページのJavaScript描画後の商品情報も取得を試みます。Chromiumは商品情報取得時に未導入であれば自動インストールされます。初回のみダウンロードが発生するため、取得に時間がかかる場合があります。

自動インストールを無効にしたい場合は、`application.properties` の `scraping.browser.auto-install=false` に変更してください。

ログイン必須ページ、CAPTCHAやCloudflare等で保護されたページ、利用規約で自動取得が禁止されているページは取得対象外です。これらを回避する処理や、ログイン情報を使った自動取得は行いません。
