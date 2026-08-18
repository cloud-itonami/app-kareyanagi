# app-kareyanagi

**`kareyanagi` の appview —— この repo が持つのは公開面 1 枚だけである。**
名前が機能を示さないので先に名乗る（この workspace の規約）。

`etzhayyim/root` の `60-apps/etzhayyim-project-kareyanagi` からの抽出物で、
**2026-08-18 に TypeScript/Svelte から ClojureScript へ移行した**（ADR-0001）。
数字はすべて `scripts/verify-docs-claims.cljs` が tree から再計算して検査する。

## ⚠ この repo は 2 つの identity を主張している（移行では直していない）

| 出所 | 名乗り | nanoid | tree に在るか |
|---|---|---|---|
| `PROJECT.jsonld` / `README.edn` / `CLAUDE.md` | カビ撲滅プラットフォーム（IoT カビセンサー・H3 分布） | `mcr736od` / `kpat4bp7` | **どちらも無い** |
| `appview/…/wrangler.jsonc` / `kotodama.jsonld`（**deploy される側**） | Wholesale Trade（乾物・卸売取引） | `w8p4dsvf` | 在る |

どちらが正かはオーナーの決定であって、移行が勝手に選ぶものではない。
移行がしたのは **ページが自分の名前を焼くのをやめた**ことだけである ——
公開ページは wrangler の vars が名乗るとおりに描くので、この食い違いは
ページを見れば見える。検証器は両方の文字列を pin しているので、どちらかが
黙って書き換わると落ちる。

## deploy されるものは、いま読んでいるソースである

```
src/kareyanagi/route.cljc    判断（どの handler が答えるか）  ← 純 .cljc、テスト対象
src/kareyanagi/view.cljc     ページ（jp-go-dds の hiccup）    ← 純 .cljc、テスト対象
src/kareyanagi/worker.cljs   Request/Response に触る唯一の層
        ↓ shadow-cljs :target :esm
dist/worker.js               ← wrangler.jsonc の "main" が指すもの
```

移行前の `main` は `svelte/.svelte-kit/cloudflare/_worker.js` を指していた。
**このパスは tree に無い**（`git ls-files` にも、ディレクトリとしても）。
deploy されるのは SvelteKit の *ビルド出力* で、repo が持っているのはその
*入力* だけだった。同時に `src/app.ts` は **この repo のどこからも参照されて
いなかった** —— wrangler も vite も svelte.config も指していない。いちばん
アプリらしく読めるファイルが、どの bundle にも入っていなかった（ADR-0001）。

いまは `main` が指す bundle が上のソースからコンパイルされたものなので、その
形は構造的に起こり得ない。`scripts/verify-docs-claims.cljs` が
**shadow の出力先と wrangler の `main` と export の ns 名の 3 つが噛み合って
いること**を検査し、噛み合わなくなれば落ちる。

判断を `.cljc` に置いてあるのは、ブラウザもビルドも無しにテストするためであり、
ingress capability が qualify した時に **最初に `.kotoba` へ移る部分**だからで
ある（入口を当面 cljs に置くのは ADR-2606290000 の判断）。

## 公開ルート

| METHOD | PATH | 何をするか |
|---|---|---|
| GET | `/` | この appview の説明ページ |
| GET | `/health` | 生存確認。deploy された面が答えることを外から確かめられる |
| POST | `/xrpc/:nsid` | XRPC を AgentGateway MCP router へ中継する |
| OPTIONS | `/xrpc/*` | CORS preflight |

**この表の出所は `kareyanagi.route/routes` で、ページもそこから描く。** 移行前の
ページは `routeCount: 0` / `routes: []` / `vars: []` / `title: "Kareyanagi Mcp
Component"` を literal で抱えており、隣の `wrangler.jsonc` が route 2・var 8・
別の表示名を宣言していることに気づけなかった。いまは route 表も env も渡す側が
持ち、ページは描くだけなので、両者がずれる余地が無い。

`/health` は **移植ではなく追加**である。deploy されていた SvelteKit の面には
`/` と `/xrpc/[...path]` しか無かった（`src/app.ts` には在ったが、それはどこにも
deploy されていない）。あとで「移行前からあった」と読まれないようにここに書く。

## いま在るもの — 19 ファイル

| 面 | ファイル |
|---|---|
| 判断・描画・edge | `src/kareyanagi/{route.cljc, view.cljc, worker.cljs}` |
| テスト | `test/kareyanagi/route_test.cljc`（6 tests / 33 assertions） |
| ビルド | `deps.edn` / `shadow-cljs.edn` / `.gitignore` |
| Worker 設定 | `appview/kareyanagi-mcp-component/wrangler.jsonc` |
| actor 記述子 | `appview/kareyanagi-mcp-component/kotodama.jsonld` |
| 検査 | `scripts/{smoke-worker.cljs, verify-docs-claims.cljs}` |
| 設計 | `CLAUDE.md` |
| 由来・識別 | `PROJECT.jsonld` / `README.edn` / `migration.edn` |
| appview ではないもの | `bpmn/kareyanagi-control.bpmn` |
| 文書 | `README.md` / `docs/operator-quickstart.md` / `docs/adr/0001-*.edn` |

**production の TypeScript は 0 本、正本言語（`.cljs`/`.cljc`）が 4 本。**
移行前は 5 対 0 だった。この 2 つは検証器の claim なので、TS が戻れば落ちる
——撤去した 12 パスに戻る場合（`removed-by-migration-absent`）も、別名で入る
場合（`production-ts-files`）も、別々の claim が捕まえる。

### appview ではないもの — `bpmn/kareyanagi-control.bpmn`

BPMN のプロセス定義が 1 本（5,777 バイト）ある。Zeebe/Camunda 向けで、8 つの
`bpmn:process` が `com.etzhayyim.apps.kareyanagi.{createListing, listListings,
createOrder, listOrders, updateInventory, getInventory, processTrade,
getTradeHistory}` を service task の type として宣言している —— **wrangler.jsonc の
`APP_CAPABILITIES` 8 個と 1 対 1 で一致する**。つまりこれは上の表の
「Wholesale Trade 側」の identity を裏づける資料でもある。

**これは移行していないし、消してもいない。** どの bundle にも入らず、置き換えた
対象（SvelteKit のビルド出力）が参照してもいない。それを消費するワークフロー
エンジンはこの repo に無い —— 消せば移行ではなく破壊である。検証器が `.bpmn` の
本数を 1 に pin しているので、黙って増えて第 2 の未移行サーフェスになることも
ない。

なお **この repo に「appview ではない TypeScript」は無かった**。TS 5 本はすべて
appview のものだった（`src/app.ts` / svelte の `+server.ts` と `vite.config.ts` /
`vitest.config.ts` / `test/kareyanagi.test.ts`）。

## ページが出す値・出さない値

env の**キー名**は全部出す。**値を出すのは 3 つだけ**:
`APP_DISPLAY_NAME` / `APP_DESCRIPTION`（この面が自分を何と名乗るか。
`kotodama.jsonld` で公開されているのと同じ値）と
`AGENTGATEWAY_MCP_ROUTER_URL`（どこへ中継するかは運用者が見る必要がある）。
それ以外の値は出さない。集合は `kareyanagi.route/shown-var-keys` に在る。

smoke はこれを**独立した印 2 種**で見る: 表示しない var（`APP_UI_TYPE`）に
置いた sentinel が出ていないこと、そして表示する値（表示名と中継先）が
出ていること。**片方だけだと「全部隠す」実装も「全部出す」実装も通ってしまう。**

## UI

基盤は `kotoba-lang/jp-go-digital-design-system`（デジタル庁デザインシステム）。
色・寸法は `--hig-*` トークン契約だけで書き、raw hex も px フォントサイズも
置かない。app 固有 CSS は 3 行。CSS は外部リクエストゼロの方針どおり
`shadow.resource/inline` で bundle に焼く。

決定論的 audit（`kotoba-lang/design-quality`）で **100.00 / 100（gate 95）**。
12 軸すべてを適用しても 100.00。

### デザインシステムの検査は 2 本ある

`dads-table` が在ることを 1 本で見る形は**落ちない検査**である —— それは view が
出力する markup であって、CSS が 1 バイトも入っていないページにも現れる。
実測（このページ、2026-08-18）:

| 探す文字列 | CSS 込み | CSS 無し |
|---|---|---|
| `dads-table` | 74 | **6**（0 にならない） |
| `class="dads-table"` | 1 | **1**（変わらない） |
| `--color-primitive-blue` | 45 | **0** |

だから 2 本に割った。**component を使ったか**（`class="dads-table"`）と、
**stylesheet が実際に入ったか**（`--color-primitive-blue`）は別の主張である。

design-quality のスコアはこの区別をしない —— **デザインシステムを完全に外した
ページでも 96.63 で PASS する**（実測。同じページから CSS だけ抜いた）。
さらに CLI 自身が「10 軸しか当てていない」と出力に書く。「デザインシステムが
在る」と言えるのはこの smoke の 2 本目だけである。

## 呼び先が 1 つも解決しない（移行では直らない）

| ホスト | 役割 | DNS（2026-08-18 実測） |
|---|---|---|
| `kareyanagi.etzhayyim.com` | 公開ホスト（wrangler の route） | **NXDOMAIN** |
| `w8p4dsvf.etzhayyim.com` | 同（nanoid 側） | **NXDOMAIN** |
| `mcp.etzhayyim.com` | `/xrpc/:nsid` の中継先 | **NXDOMAIN** |
| `dispatcher.etzhayyim.com` | 移植しなかった `src/app.ts` の宛先 | **NXDOMAIN** |

親の `etzhayyim.com` は解決する（Cloudflare NS）。子が 1 つも無い。
deploy 先も中継先も、いま存在しない。`/xrpc/` は到達できなければ **502 を返す**
——成功と同じ形で隠さない。

## 由来（custody）

`migration.edn` は出所を `etzhayyim/root` の tree `15727d19` と宣言し、
`:allowed-additions` に `README.edn` と `migration.edn` を持つ。移行後の状態:

- 継承した 5 ファイル（10,588 バイト）は**いまも 1 バイトも変わっていない**
  （sha256 を検証器に固定）: `PROJECT.jsonld` / `README.edn` / `migration.edn` /
  `kotodama.jsonld` / `bpmn/kareyanagi-control.bpmn`
- `wrangler.jsonc` は**意図的に変更**した（`main` の付け替え、存在しない
  SvelteKit client を指す `assets` の撤去、`compatibility_flags` の撤去、
  `APP_FRAMEWORK` を `sveltekit-edge-bff` → `cljs-esm-worker`）。
  `nodejs_compat` / `nodejs_als` は adapter-cloudflare が要求していたもので、
  **撤去は憶測ではなく実測のあとに行った** —— flags を外した設定のまま
  `wrangler dev --local`（workerd 4.124.0）で起こし、7 経路すべてが期待どおり
  答えることを確認している（`docs/operator-quickstart.md` §4.6）。
  `rules`（CompiledWasm）はこの移行の対象ではないので**触っていない**
- `CLAUDE.md` も**意図的に変更**した（tree に実在するものを測って記録した節を
  追加。元の文書は tree に無い 2 component と Svelte 5 UI の計画を、在るものの
  記述であるかのように書いていた）
- TypeScript/Svelte の 12 ファイルは**移行で撤去**した。検証器はその 12 パスを
  名指しで「不在であること」を検査する —— byte 合計は「TS が消えた」と言えない

## 持ち越さなかったもの（黙って消していない）

移行前の `src/app.ts` にあってどこにも deploy されていなかった経路のうち:

- `/xrpc/com.etzhayyim.apps.kareyanagi.*` → `dispatcher.etzhayyim.com` への
  proxy —— 宛先が **NXDOMAIN**、かつ `DISPATCHER_URL` /
  `DISPATCHER_INTERNAL_SECRET` の binding が `wrangler.jsonc` に **1 つも無い**
- `/_app/meta` —— `/health` と同じ本文を返す別名。deploy された面には無かった

**動かない経路を移植して「移行済み」と言わないため**である。必要になった時点で
`route.cljc` に足し、テストと binding を伴って戻す。

## 検証

```bash
npx --yes nbb scripts/verify-docs-claims.cljs .     # <dir> は先頭に置く
```

exit 0 = 全一致 / 1 = 食い違い / **2 = 判定できなかった**（0 と区別する）。
テストとビルドと smoke は `docs/operator-quickstart.md`。

**すべての gate は、緑を受け取る前に赤くして確かめてある。** 19 の mutation を
1 つずつ当て、対応する検査 *だけ* が落ちることを見た（一覧は `docs/adr/0001`）。
とくに: `:warnings-as-errors` を `:build-options` に移すと **grep では 3 件
見つかるのに** 検証器は落ちる、CSS を bundle から抜くと 2 本のうち
stylesheet 側だけが赤くなる、env の値を漏らすと『隠す』側だけが赤くなる。
