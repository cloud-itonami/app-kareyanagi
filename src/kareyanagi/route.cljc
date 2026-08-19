(ns kareyanagi.route
  "どの handler がリクエストに答えるか —— データと、それを読む純関数。

  `.cljs` ではなく `.cljc` なのは意図的である。edge worker で検査する価値が
  あるのは routing の判断そのもので、ここに置けばブラウザもビルドもネット
  ワークも無しにテストできる。`kareyanagi.worker` が Request/Response に触る
  唯一の名前空間であり、この file が既に決めたこと以外は何もしない。

  ingress capability が qualify した時（今日は `:native-aot`/`:wasm-aot` とも
  pending —— ADR-2606290000）、最初に `.kotoba` へ移るのもここである。route 表は
  スカラと文字列の上の判断で、それはちょうどその移行を生き延びる形をしている。"
  (:require [clojure.string :as str]))

(def routes
  "公開面をデータとして持つ。**ランディングページはこれを描く** ——
  だから『実際に答える route』と『ページが宣伝する route』がずれる余地が無い。

  移行前の `+page.svelte` は `routeCount: 0` と `routes: []` を literal で
  抱えており、隣の `wrangler.jsonc` が route 2 本と var 8 個を宣言している
  ことに気づけなかった（docs/adr/0001）。"
  [{:route/path "/"           :route/method :get  :route/kind :page
    :route/doc "この appview の説明ページ"}
   {:route/path "/health"     :route/method :get  :route/kind :json
    :route/doc "生存確認。deploy された面が答えることを外から確かめられる"}
   {:route/path "/xrpc/:nsid" :route/method :post :route/kind :proxy
    :route/doc "XRPC を AgentGateway MCP router へ中継する"}])

(defn- xrpc-nsid
  "`/xrpc/<nsid>` の nsid。**空文字だけが nil**。

  多段パス（`/xrpc/a/b`）もそのまま通す。移行前の SvelteKit route は rest
  parameter `[...path]` で受けており、`a/b` を tool 名としてそのまま転送して
  いた（空文字だけが 400）。ここで 1 セグメントに絞ると挙動が変わる ——
  NSID に `/` は現れないので上流で失敗するだけだが、**それは移行ではなく
  方針変更**であって、移行の commit に紛れ込ませてよいものではない。

  同型の移行（cloud-itonami/app-lo、app-ongakuka）で先にこの区別が正しく
  行われており、こちらを合わせた。絞るなら別の決定として記録する。"
  [path]
  (when (str/starts-with? path "/xrpc/")
    (let [rest' (subs path (count "/xrpc/"))]
      (when (seq rest') rest'))))

(defn dispatch
  "method + path → 何をするか。Request も Response も知らない。

  返すのは `{:action …}` で、`:action` は
  `:page` / `:health` / `:xrpc` / `:cors-preflight` / `:not-found` /
  `:method-not-allowed` / `:bad-request` のいずれか。"
  [method path]
  (let [m (keyword (str/lower-case (or method "get")))
        p (or path "")]
    (cond
      (and (= m :options) (str/starts-with? p "/xrpc/"))
      {:action :cors-preflight}

      (str/starts-with? p "/xrpc/")
      (if (= m :post)
        (if-let [nsid (xrpc-nsid p)]
          {:action :xrpc :nsid nsid}
          {:action :bad-request :reason "Missing XRPC method"})
        {:action :method-not-allowed :allow "POST, OPTIONS"})

      (= p "/health") (if (= m :get)
                        {:action :health}
                        {:action :method-not-allowed :allow "GET"})
      (= p "/")       (if (= m :get)
                        {:action :page}
                        {:action :method-not-allowed :allow "GET"})
      :else {:action :not-found})))

(defn mcp-router-url
  "env の設定 → MCP router の URL。末尾スラッシュは落とす。

  既定値をここに焼くのは、設定が無いときに黙ってどこかへ POST しないためでは
  なく、**どこへ行くのかを 1 箇所で読めるようにする**ためである。移行前の
  `+server.ts` の `mcpRouterUrl()` と同じ優先順位:
  `AGENTGATEWAY_MCP_ROUTER_URL` → `MCP_ROUTER_URL` → 既定。"
  [{:keys [AGENTGATEWAY_MCP_ROUTER_URL MCP_ROUTER_URL]}]
  (let [pick (fn [s] (when (and (string? s) (seq (str/trim s))) (str/trim s)))]
    (-> (or (pick AGENTGATEWAY_MCP_ROUTER_URL)
            (pick MCP_ROUTER_URL)
            "https://mcp.etzhayyim.com/xrpc/com.etzhayyim.mcp.message")
        (str/replace #"/+$" ""))))

(def shown-var-keys
  "**値そのものをページに出す** env のキー。これ以外はキー名だけを出す。

  中継先（`AGENTGATEWAY_MCP_ROUTER_URL`）は、どこへ中継されるのかを運用者が
  見る必要があるので出す。表示名と説明（`APP_DISPLAY_NAME` /
  `APP_DESCRIPTION`）は、この appview が『自分を何と名乗るか』そのもので、
  kotodama.jsonld で公開されている値と同じものである。

  この集合を明示するのは、smoke が **2 つの独立した印**で検査するため:
  ここに無い var の値がページに出ていないこと、そしてここに在る var の値が
  出ていること。片方だけだと『全部隠す』実装も『全部出す』実装も通る。"
  #{:AGENTGATEWAY_MCP_ROUTER_URL :APP_DISPLAY_NAME :APP_DESCRIPTION})

(defn identity-of
  "env → この面が名乗る identity。**ページに焼かない。**

  wrangler.jsonc の vars が正本で、無ければ repo 名に落ちる。移行前のページは
  `title: \"Kareyanagi Mcp Component\"` を literal で持っており、同じ
  ディレクトリの設定が別の名前（`Wholesale Trade`）を宣言していることを
  ページ側からは知りようがなかった。"
  [{:keys [APP_DISPLAY_NAME APP_DESCRIPTION APP_NANOID]}]
  {:display-name (or (not-empty (str APP_DISPLAY_NAME)) "kareyanagi")
   :description  (not-empty (str APP_DESCRIPTION))
   :nanoid       (not-empty (str APP_NANOID))})

(def ^:private drop-headers
  "上流へ渡さない header。

  `host` —— 移行前の SvelteKit route も削っていた（宛先が変わるので嘘になる）。
  `content-length` / `content-encoding` —— body を JSON-RPC の封筒に詰め直す
  ので、元の長さもエンコーディングも当てはまらない。

  **それ以外は全部渡す。** 移行前は `new Headers(request.headers)` から host を
  削るだけで、`authorization` も上流に届いていた。移行で 3 つの header を新規に
  作る形にしたとき、それが黙って消えていた —— しかも preflight は
  `access-control-allow-headers: content-type,authorization` と許可を宣言した
  ままだったので、ブラウザには送ってよいと言いながら捨てていたことになる。"
  #{"host" "content-length" "content-encoding"})

(defn relay-headers
  "受け取った header を、上流へ渡す形にする。`in` は [[k v] …] の列。

  ここが `.cljc` にあるのは、これがビルドもブラウザも無しに固定できる**判断**
  だからである。`js/Headers` を worker 側で組み立てる形にすると、何が渡って
  何が落ちるかを述べたテストが書けない —— そしてこの欠陥は、まさに誰も
  『何が転送されるか』を訊かなかったから 21 repo で生き延びた。"
  [in nsid]
  (into {"content-type" "application/json"
         "x-etzhayyim-bff" "cljs-worker"
         "x-etzhayyim-xrpc-method" nsid}
        (comp (remove (fn [[k _]] (contains? drop-headers (str/lower-case k))))
              (map (fn [[k v]] [(str/lower-case k) v])))
        in))

(defn unwrap-mcp
  "MCP router の応答から、呼び手に返す値を取り出す。移行前の `+server.ts` と
  同じ剥がし方: `{:result {:structuredContent X}}` → X、`{:result X}` → X、
  それ以外は素通し。`{:error …}` は呼び出し側が 502 にするので判定だけ返す。"
  [payload]
  (cond
    (and (map? payload) (contains? payload :error))
    {:ok? false
     :error (get-in payload [:error :message] "MCP router returned an error")
     :upstream payload}

    (and (map? payload) (contains? payload :result))
    (let [r (:result payload)]
      {:ok? true :value (if (and (map? r) (contains? r :structuredContent))
                          (:structuredContent r)
                          r)})

    :else {:ok? true :value payload}))
