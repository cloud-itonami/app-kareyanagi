(ns kareyanagi.view
  "この appview の説明ページ。純 hiccup。

  基盤は `jp-go-dds`（デジタル庁デザインシステム）—— superproject の skill
  `kotoba-uiux` が定める新規 UI の base。色・寸法は `--hig-*` トークン契約で
  書き、raw hex も px フォントサイズも置かない。

  **表示する事実はすべて引数で受け取る。ページの中に焼かない。**
  これは装飾の都合ではなく、この移行が消しに来た欠陥そのものへの答えである
  —— 移行前の `+page.svelte` は `routeCount: 0` / `routes: []` / `vars: []` /
  `title: \"Kareyanagi Mcp Component\"` を literal で抱えており、隣の
  `wrangler.jsonc` が route 2 本・var 8 個・別の表示名を宣言していることに
  気づけなかった（docs/adr/0001）。ここでは route 表も env も渡す側が持ち、
  ページは描くだけなので、両者がずれる余地が無い。"
  (:require [jp-go-dds.core :as dds]
            [jp-go-dds.page :as page]
            [jp-go-dds.tokens :as tokens]
            [clojure.string :as str]))

(def app-css
  "app 固有の最小 CSS。`--hig-*` 契約だけを使う（bridge が DADS の上に再定義
  する）。DADS を base にした app の下には `shitsuke.hig` が居ないので、bridge が
  運んでいないトークンは何にも解決しない —— 使うのは運ばれている 71 個の中だけ。"
  (str/join
   "\n"
   [".kar-lede { color: var(--hig-color-secondary-label); max-width: 42rem; }"
    ".kar-note { color: var(--hig-color-secondary-label); font-size: var(--hig-text-footnote-font-size); }"
    ".kar-mono { font-family: var(--hig-font-mono); overflow-wrap: anywhere; }"]))

(defn- route-rows [routes]
  (mapv (fn [r]
          [(str/upper-case (name (:route/method r)))
           [:span {:class "kar-mono"} (:route/path r)]
           (:route/doc r)])
        routes))

(defn body
  "opts:
   :identity  `route/identity-of` の戻り値（env が名乗る表示名・説明・nanoid）
   :routes    `route/routes`（この Worker が実際に答えるもの）
   :vars      env のキー（**キー名だけ**。値は `:identity` と `:mcp-url` を除き出さない）
   :mcp-url   XRPC の中継先（`route/mcp-router-url` の戻り値）
   :built-at  bundle のビルド時刻（不明なら nil）"
  [{:keys [routes vars mcp-url built-at] :as opts}]
  ;; `:identity` を `:keys` で受けると local が `clojure.core/identity` を
  ;; 隠す。ここでは明示的に取り出して、その影を作らない。
  (let [{:keys [display-name description nanoid]} (:identity opts)]
    (dds/container
     (dds/section
      {}
      (dds/heading 1 display-name)
      [:p {:class "kar-lede"}
       (or description
           "この appview の公開面。表示名と説明は wrangler.jsonc の vars から来る。")]
      (when nanoid
        [:p {:class "kar-note"} "nanoid: " [:span {:class "kar-mono"} nanoid]]))

     (dds/section
      {:title "この面が答えるもの"}
      (dds/table {:caption "公開ルート"
                  :headers ["METHOD" "PATH" "何をするか"]
                  :rows (route-rows routes)})
      [:p {:class "kar-note"}
       "この表は Worker の route 表そのものから描いている。ページに焼いた値では"
       "ないので、実際に答えるものと表示がずれない。"])

     (dds/section
      {:title "実行時の設定"}
      (if (seq vars)
        [:div
         (into [:p] (interpose " " (map (fn [k] (dds/chip-label (name k))) vars)))
         [:p {:class "kar-note"}
          "キー名のみ。" [:strong "値を出すのは 3 つだけ"] " —— 表示名・説明"
          "（上に描いているもの）と、下の中継先である。どこへ中継するかは運用者が"
          "見る必要があるので意図的に出している。それ以外の値は出さない。"]]
        [:p {:class "kar-note"} "env が渡されていない（ローカル描画）。"])
      [:p {:class "kar-note"} "XRPC の中継先: "
       [:span {:class "kar-mono"} mcp-url]])

     (dds/section
      {:title "現在地"}
      [:p {:class "kar-lede"}
       "この appview は TypeScript/Svelte から ClojureScript へ移行済み。"
       "deploy される bundle は、いま読んでいるソースからコンパイルされたもので"
       "ある（docs/adr/0001）。"]
      (when built-at
        [:p {:class "kar-note"} "bundle build: " built-at])))))

(defn render
  "完全な HTML 文書。`css` は呼び出し側が渡す（ライブラリは I/O を持たない）。"
  [{:keys [css] :as opts}]
  (page/->page
   {:title (:display-name (:identity opts))
    :description (or (:description (:identity opts)) "kareyanagi appview")
    :lang "ja"
    :css css
    :app-css (str tokens/bridge-css "\n" app-css)}
   (body opts)))
