# operator-quickstart

**この repo で今日実際にできることを、踏める形で上から書く。** 所要 5 分。
Cloudflare のアカウントは要らない（deploy だけが要る。§5）。

出力はすべて実際に walk した結果である。

## 0. 前提

| 要るもの | 確認 | この walk で使った版 |
|---|---|---|
| git | `git --version` | 2.51.0 |
| node | `node --version` | v26.3.0 |
| nbb | `npx --yes nbb --version` | v1.4.208 |
| clojure | `clojure --version` | ビルド時のみ |

## 1. 取得して、書いてあることが本当か検査する

```bash
git clone git@github.com:cloud-itonami/app-kareyanagi.git
cd app-kareyanagi
REPO=$PWD
npx --yes nbb scripts/verify-docs-claims.cljs .
```

実際の出力:

```
SCANNED	19
PASS	tracked-files	expected=19	actual=19
PASS	inherited-bytes	expected=10588	actual=10588
PASS	preserved-files-unchanged	expected=[]	actual=[]
PASS	removed-by-migration-absent	expected=[]	actual=[]
PASS	svelte-artifacts	expected=0	actual=0
PASS	production-ts-files	expected=0	actual=0
PASS	production-canonical-files	expected=4	actual=4
PASS	bpmn-files	expected=1	actual=1
PASS	wrangler-main	expected="../../dist/worker.js"	actual="../../dist/worker.js"
PASS	declared-vars	expected=8	actual=8
PASS	declared-routes	expected=2	actual=2
PASS	app-framework	expected="cljs-esm-worker"	actual="cljs-esm-worker"
PASS	wrangler-display-name	expected="Wholesale Trade"	actual="Wholesale Trade"
PASS	no-stale-assets-binding	expected=true	actual=true
PASS	sveltekit-compat-flags	expected=0	actual=0
PASS	shadow-builds-that-main	expected=true	actual=true
PASS	warnings-as-errors-in-compiler-options	expected=true	actual=true
PASS	readme-edn-role	expected=true	actual=true
PASS	claude-md-describes-cljs	expected=true	actual=true
PASS	page-renders-route-table	expected=true	actual=true
OK	every claim in README.md and docs/operator-quickstart.md holds
```

末尾が `OK` なら README の数値・存在・不在は tree と一致している。
**exit 2（UNDETERMINED）は 0 ではない** —— tree を読み切れなかったという別の
答えで、「検査して問題なし」と混ぜない。

この検査には移行の不変条件が入っている: TypeScript が戻っていないこと（撤去した
12 パスの不在 + `.ts` の総数）、`wrangler.jsonc` の `main` が shadow の出力先を
指していること、`:warnings-as-errors` が `:compiler-options` の下に在ること
（EDN として読んで確かめる。**grep しない** —— このファイル自身のコメントが
その文字列を含むので、部分文字列検索は散文だけで通ってしまう）、ページが
route 表と env から描かれていること、そして `.bpmn` が 1 本のままであること。

## 2. テストを走らせる（ビルド不要・ブラウザ不要）

判断（`route.cljc`）と描画（`view.cljc`）は純 `.cljc` なので、nbb だけで回る。

```bash
K=~/github/com-junkawasaki/orgs/kotoba-lang
CP="src:test:$K/jp-go-digital-design-system/src:$K/html/src:$K/css/src"
d=$(mktemp -d)
cat > "$d/run.cljs" <<'EOF'
(require '[cljs.test :refer [run-tests]] 'kareyanagi.route-test)
(run-tests 'kareyanagi.route-test)
EOF
npx --yes nbb --classpath "$CP" "$d/run.cljs"
```

実際の出力:

```
Testing kareyanagi.route-test

Ran 6 tests containing 33 assertions.
0 failures, 0 errors.
```

何を固定しているか: `/xrpc/` は**空の nsid だけ** 400 にする（`/xrpc/a/b` は
移行前の rest parameter `[...path]` と同じく転送する。1 セグメントに絞るのは
移行ではなく方針変更）、MCP router の URL 解決（空白だけの設定は未設定として
扱う）、`result` / `structuredContent` の剥がし方、**表示名が env から来ること**、
そして**ページが route 表から描かれること**（固定値を焼いていたら落ちる）。

## 3. ページを描画して採点する

```bash
K=~/github/com-junkawasaki/orgs/kotoba-lang
CP="src:$K/jp-go-digital-design-system/src:$K/html/src:$K/css/src"
d=$(mktemp -d)
cat > "$d/render.cljs" <<'EOF'
(require '["node:fs" :as fs] '[kareyanagi.view :as view] '[kareyanagi.route :as route])
(let [css (.readFileSync fs (str (.-DDS js/process.env) "/resources/jp_go_dds/dds.css") "utf8")
      env {:APP_DISPLAY_NAME "Wholesale Trade"
           :APP_NANOID "w8p4dsvf"
           :APP_UI_TYPE "yoro"
           :AGENTGATEWAY_MCP_ROUTER_URL "https://mcp.etzhayyim.com/xrpc/com.etzhayyim.mcp.message"}]
  (.writeFileSync fs (.-OUT js/process.env)
    (view/render {:css css :identity (route/identity-of env) :routes route/routes
                  :vars (sort (keys env)) :mcp-url (route/mcp-router-url env)}))
  (println "ok"))
EOF
DDS="$K/jp-go-digital-design-system" OUT="$d/page.html" npx --yes nbb --classpath "$CP" "$d/render.cljs"

cd $K/design-quality && npx --yes nbb -m design-quality.cli score "$d/page.html" --min 95
```

実際の出力（末尾）:

```
  100.00  /…/page.html
aggregate: 100.00

axes scored: 10 (viewport, safe-area, dynamic-viewport, tap-targets, focus-visible, reduced-motion, overflow-guard, color-scheme, responsive, semantics)
NOT scored: input-zoom, contrast — pass --extra-axes to include the optional ones
A pass says nothing about an axis that was not applied.
gate: aggregate 100.00 >= min 95.00 -> PASS
```

`--extra-axes` を付けて 12 軸すべてを当てても 100.00 / PASS。

**このスコアが何を証明しないか。** 同じページから DADS の CSS だけを抜いて
（`:css ""`）採点すると **96.63 で PASS する**（実測 2026-08-18）。
デザインシステムが 1 バイトも入っていなくてもこの gate は通る。
「入っている」と言えるのは §4.5 の smoke の 2 本目だけである。

## 4. bundle をビルドする

**高負荷ビルドは同時 1 本に制限されている**（superproject `CLAUDE.md` の
resource governor）。直接叩かず、必ず guard 経由で:

```bash
cd "$REPO"
node ~/github/com-junkawasaki/scripts/resource-guard.mjs run build -- \
  npx --yes shadow-cljs release worker
ls -la dist/worker.js
```

lock を他セッションが持っていると exit 2 で拒否される。**迂回しない** ——
`resource-guard: build is already running (pid=…)` はエラーではなく順番待ちで
ある（この walk では 4 つの並行セッションと競合し、最初のビルドで約 10 分、
mutation の連続では 1 本あたり 1〜4 分待った）。

実際の出力（末尾）:

```
[:worker] Build completed. (55 files, 12 compiled, 0 warnings, 18.35s)

$ ls -la dist/worker.js
-rw-r--r--  1 junkawasaki  wheel  246084  8月 18 22:50 dist/worker.js
$ shasum -a256 dist/worker.js
5a0f1580df5aa7796cefc695059f39d74778e3e77d63ee49154f967270db630a
```

### 壊れた var はビルドを **落とす**（2026-08-18 実測）

`shadow-cljs.edn` の `:compiler-options` に `:warnings-as-errors true` を入れた。
入れる前は、存在しない var を参照しても shadow は **WARNING** を出して **exit 0**
し、最初のリクエストで `Cannot read properties of undefined` を投げる bundle を
書いていた ——「ビルドが通った」は検査ではなかった（**落ちようがなかった**）。

この repo で実際に落として確かめた。**注目すべきは最後の行**
（`:shadow.build.compiler/warning-as-error true`）—— warning が error に
変換されたことを shadow 自身が言っている:

```
MUTATION-[:worker] Build completed. (55 files, 12 compiled, 0 warnings, 18.35s)

$ ls -la dist/worker.js
-rw-r--r--  1 junkawasaki  wheel  246084  8月 18 22:50 dist/worker.js
$ shasum -a256 dist/worker.js
5a0f1580df5aa7796cefc695059f39d74778e3e77d63ee49154f967270db630a
```

| | exit | `dist/worker.js` sha256 | bytes |
|---|---|---|---|
| 改名前 | **0** | `5a0f1580…70db630a` | 246,084 |
| 改名後 | **1** | `5a0f1580…70db630a`（**不変**） | 246,084 |
| 戻して再ビルド | **0** | `5a0f1580…70db630a` | 246,084 |

**落ちたビルドは bundle を出荷しない** —— sha256 が 1 バイトも動いていないことが
それを言っている。この 3 行目は 4 つの mutation を当てたあとの最終ビルドでもあり、
**復元したソースから baseline と 1 バイト違わない bundle が出た**ことを示す
（このマシンでは 4〜5 個の agent が並行して走り、`/tmp` を共有している ——
「復元したファイルが自分のものだった」ことは sha でしか言えない）。

キーは `:build-options` ではなく **`:compiler-options`** に置く。shadow が読むのは
`[:compiler-options :warnings-as-errors]` で、置き場所を間違えると**黙って無視される**
—— この option が防ぐはずの失敗（落ちようのない検査）そのものになる。
だから検証器はここを **EDN として読む**（grep ではなく）。

## 4.5 ビルドした成果物を実際に叩く

ここが deploy されるものに触る唯一の検査である。

```bash
cd "$REPO" && npx --yes nbb scripts/smoke-worker.cljs dist/worker.js
```

```
PASS	default export has fetch	expected=true	actual=true
PASS	GET / status	expected=200	actual=200
PASS	GET / is html	expected=true	actual=true
PASS	page advertises /health	expected=true	actual=true
PASS	page advertises /xrpc/:nsid	expected=true	actual=true
PASS	page shows a var key	expected=true	actual=true
PASS	page hides other var values	expected=false	actual=false
PASS	page shows the relay target it uses	expected=true	actual=true
PASS	page shows the display name the config declares	expected=true	actual=true
PASS	page uses the design system components	expected=true	actual=true
PASS	page carries the stylesheet itself	expected=true	actual=true
PASS	GET /health status	expected=200	actual=200
PASS	health names its routes	expected=true	actual=true
PASS	POST /xrpc/ status	expected=400	actual=400
PASS	single-segment nsid is relayed (unreachable -> 502)	expected=502	actual=502
PASS	multi-segment nsid is relayed the same way	expected=502	actual=502
PASS	unreachable relay is not hidden as success	expected=true	actual=true
PASS	OPTIONS preflight	expected=204	actual=204
PASS	unknown path	expected=404	actual=404
PASS	wrong method	expected=405	actual=405
OK	the built bundle answers as the route table says
```

**bundle が無ければ exit 2**（「判定できなかった」であって合格ではない）。

## 4.6 Workers ランタイム（workerd）で動かす

Node で import する smoke より強い検査。実際の workerd で起こす。

```bash
cd "$REPO/appview/kareyanagi-mcp-component"
npx --yes wrangler@latest dev --local --port 8817 --ip 127.0.0.1
# 別シェルで
B=http://127.0.0.1:8817
curl -s -o /dev/null -w '%{http_code} %{content_type}\n' $B/
curl -s $B/health
curl -s -X POST -w ' [%{http_code}]' $B/xrpc/
curl -s -X POST -d '{}' -w ' [%{http_code}]' $B/xrpc/a/b
curl -s -X OPTIONS -o /dev/null -w '%{http_code}\n' $B/xrpc/x
curl -s -o /dev/null -w '%{http_code}\n' $B/nope
curl -s -X POST -o /dev/null -w '%{http_code}\n' $B/health
```

実際の出力（wrangler 4.124.0、`compatibility_flags` を **削除した設定のまま**）:

```
GET /            200 text/html; charset=utf-8
GET /health      {"ok":true,"app":"kareyanagi","runtime":"cljs","routes":["/","/health","/xrpc/:nsid"]} [200]
POST /xrpc/      {"error":"Missing XRPC method"} [400]
POST /xrpc/a.b   {"error":"MCP router unreachable","detail":"internal error; reference = ull7019o56fn1tcbjhn7355e","url":"https://mcp.etzhayyim.com/xrpc/com.etzhayyim.mcp.message"} [502]
POST /xrpc/a/b   {"error":"MCP router unreachable","detail":"internal error; reference = sselpcegs7dprt0o886tnb77","url":"https://mcp.etzhayyim.com/xrpc/com.etzhayyim.mcp.message"} [502]
OPTIONS /xrpc/x  204
GET /nope        404
POST /health     405
```

workerd が描いたページ（82,021 バイト）の中身も数えた:
`class="dads-table"` 1 / `--color-primitive-blue` 45 / `/xrpc/:nsid` 1 /
`Wholesale Trade` 4 / `w8p4dsvf` 1。

`compatibility_flags`（`nodejs_compat` / `nodejs_als`）は SvelteKit の
adapter-cloudflare 由来で、この bundle には要らない。**撤去は憶測ではなく
この実測で確かめてから行った。** 単一セグメントと多段パスが同じ 502 になることも、
ここでは**実 DNS で**確認している（`mcp.etzhayyim.com` は NXDOMAIN）。

## 5. deploy

```bash
cd "$REPO/appview/kareyanagi-mcp-component"
npx wrangler deploy
```

**この walk では deploy していない。** そして route が指すホストは解決しない
（`kareyanagi.etzhayyim.com` / `w8p4dsvf.etzhayyim.com` とも NXDOMAIN）。
deploy が成功しても誰も到達できない。`/xrpc/` の中継先 `mcp.etzhayyim.com` も
同様なので、到達できたとしても中継は **502 を返す**（成功と同じ形で隠さない）。

superproject の deploy guard は `origin/main` を含む checkout からの deploy しか
許さない点も併せて注意。

## 6. ここに無いもの

- `dispatcher.etzhayyim.com` への proxy と `/_app/meta` —— 移行前の `src/app.ts`
  にあり、どこにも deploy されていなかった経路。宛先が NXDOMAIN、または
  binding が `wrangler.jsonc` に無いので**持ち越していない**（README の
  「持ち越さなかったもの」）
- カビセンサー・H3 分布・撲滅タスク —— `CLAUDE.md` と `PROJECT.jsonld` が
  記述する機能。**この tree にその実装は無い**（component `mcr736od` /
  `kpat4bp7` 自体が無い）
