#!/usr/bin/env nbb
;; verify-docs-claims — re-derive every number README.md and docs/operator-quickstart.md
;; state, from the tree itself, and fail when the tree and the prose disagree.
;;
;; Before the cljs migration this file's load-bearing claim would have been a GAP:
;; wrangler.jsonc's `main` named `svelte/.svelte-kit/cloudflare/_worker.js`, a path
;; that is NOT IN THE TREE, while src/app.ts -- the file that read like the
;; application -- was referenced by nothing. That gap is closed, so the claims now
;; assert the CLOSURE, and they are written so it cannot quietly come back: the
;; TypeScript is asserted ABSENT BY NAME, not merely absent from a byte total.
;;
;; Usage:  nbb scripts/verify-docs-claims.cljs [<dir>]     (<dir> FIRST, default ".")
;; Exit:   0 every claim holds · 1 a claim is false · 2 could not answer

(require '["node:fs" :as fs]
         '["node:child_process" :as cp]
         '["node:crypto" :as crypto]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

(def root (or (first (remove #(str/starts-with? % "--") *command-line-args*)) "."))
(def APP "appview/kareyanagi-mcp-component")

(def claims
  {:tracked-files 19
   :inherited-bytes 10588          ; the 5 inherited files still carried unchanged
   :svelte-artifacts 0             ; no .svelte / svelte.config / svelte-dir file survives
   :sveltekit-compat-flags 0       ; nodejs_compat / nodejs_als were adapter-cloudflare's
   :production-ts-files 0
   :production-canonical-files 4
   :bpmn-files 1                   ; the one non-appview artifact this repo also carries
   :declared-vars 8
   :declared-routes 2
   :app-framework "cljs-esm-worker"
   :wrangler-main "../../dist/worker.js"
   :shadow-output-dir "dist"
   :shadow-export "kareyanagi.worker/handler"
   ;; The two identities this repo asserts at once (README.md "測って分かった
   ;; 食い違い"). The migration did NOT resolve this -- it only made the page
   ;; render the deployed side rather than a baked literal. Pinning both strings
   ;; means a silent edit to either one fails here and forces the prose to move
   ;; with it.
   :wrangler-display-name "Wholesale Trade"
   :readme-edn-role ":mold-eradication-operations-application"})

;; Inherited files this repository still carries BYTE-IDENTICAL.
;; wrangler.jsonc and CLAUDE.md left this set DELIBERATELY (main re-pointed, the
;; assets block and the SvelteKit compat flags removed, APP_FRAMEWORK updated;
;; CLAUDE.md gained a measured section about what is actually in the tree). Both
;; are checked by CONTENT below instead of by hash, so an intentional change and
;; an accidental one stay distinguishable.
(def preserved
  {"PROJECT.jsonld" "aba52db9982589191fd0788e4a4e17114ce1f5c2343b48c4e2ebdb7551ecd4db"
   "README.edn" "70d6ae823a202111c3a0d1c8b677eebe72b9f37ab8c468b5e88dde95dcb8b8b5"
   "migration.edn" "c1e52d669e61fbf560a9460a4126d6dfafbbc213b055b36e939e5ddf7f6b9555"
   "appview/kareyanagi-mcp-component/kotodama.jsonld"
   "32de67d6e71bc72bbbb933164b5e1e9f4df0c874ad48be9b6257231b9bc83e12"
   "bpmn/kareyanagi-control.bpmn"
   "095065904d8f49e3327dba1deca17339ab8b900dfa9a8e8432fba60f742a77fa"})

;; What the migration REMOVED, by name. A byte total cannot say "the TypeScript is
;; gone"; this can, and it fails if any of it comes back.
(def removed-by-migration
  ["appview/kareyanagi-mcp-component/src/app.ts"
   "appview/kareyanagi-mcp-component/package.json"
   "appview/kareyanagi-mcp-component/package-lock.json"
   "appview/kareyanagi-mcp-component/vitest.config.ts"
   "appview/kareyanagi-mcp-component/test/kareyanagi.test.ts"
   "appview/kareyanagi-mcp-component/svelte/package.json"
   "appview/kareyanagi-mcp-component/svelte/src/app.html"
   "appview/kareyanagi-mcp-component/svelte/src/routes/+page.svelte"
   "appview/kareyanagi-mcp-component/svelte/src/routes/xrpc/[...path]/+server.ts"
   "appview/kareyanagi-mcp-component/svelte/svelte.config.js"
   "appview/kareyanagi-mcp-component/svelte/tsconfig.json"
   "appview/kareyanagi-mcp-component/svelte/vite.config.ts"])

(def undetermined (atom []))
(def failures (atom []))
(defn undet! [m] (swap! undetermined conj m))

(defn tracked-files []
  (try (->> (.execSync cp "git ls-files" #js {:cwd root :encoding "utf8"})
            str/split-lines (remove str/blank?) vec)
       (catch :default e (undet! (str "git ls-files failed: " (.-message e))) nil)))
(defn slurp* [rel] (try (.readFileSync fs (str root "/" rel) "utf8") (catch :default _ nil)))
(defn bytes-of [rel] (try (.-size (.statSync fs (str root "/" rel))) (catch :default _ nil)))
(defn sha256 [rel]
  (try (-> (.createHash crypto "sha256") (.update (.readFileSync fs (str root "/" rel))) (.digest "hex"))
       (catch :default _ nil)))
(defn strip-jsonc [s] (str/replace s #"(?m)^\s*//.*$" ""))

(defn check! [label expected actual]
  (let [ok (= expected actual)]
    (println (str (if ok "PASS" "FAIL") "\t" (name label)
                  "\texpected=" (pr-str expected) "\tactual=" (pr-str actual)))
    (when-not ok (swap! failures conj label))
    ok))

(let [files (tracked-files)]
  (when (nil? files) (println "UNDETERMINED\tcould not list tracked files") (js/process.exit 2))
  (println (str "SCANNED\t" (count files)))
  (when (zero? (count files)) (println "UNDETERMINED\tscanned 0 files") (js/process.exit 2))

  (let [sizes (into {} (map (juxt identity bytes-of)) files)]
    (when-let [bad (seq (keep (fn [[f s]] (when (nil? s) f)) sizes))]
      (undet! (str "tracked but unreadable: " (str/join ", " bad))))

    (check! :tracked-files (:tracked-files claims) (count files))
    (check! :inherited-bytes (:inherited-bytes claims)
            (reduce + 0 (keep #(get sizes %) (keys preserved))))
    (check! :preserved-files-unchanged []
            (vec (keep (fn [[f want]] (let [got (sha256 f)]
                                        (when-not (= want got) (str f " " (or got "MISSING")))))
                       preserved)))

    ;; the TypeScript is gone, by name
    (check! :removed-by-migration-absent []
            (vec (filter #(some? (bytes-of %)) removed-by-migration)))

    ;; Svelte is gone and must not come back. removed-by-migration names the seven
    ;; svelte/ files; this catches a return under ANY name -- a new .svelte file,
    ;; a svelte.config, or a svelte/ directory.
    (check! :svelte-artifacts (:svelte-artifacts claims)
            (count (filter #(or (str/ends-with? % ".svelte")
                                (str/includes? % "svelte.config")
                                (str/includes? % "/svelte/"))
                           files)))

    ;; language of the production source
    (let [prod (remove #(str/starts-with? % "scripts/") files)]
      (check! :production-ts-files (:production-ts-files claims)
              (count (filter #(str/ends-with? % ".ts") prod)))
      (check! :production-canonical-files (:production-canonical-files claims)
              (count (filter #(re-find #"\.(cljs|cljc|clj|kotoba)$" %) prod))))

    ;; The BPMN process definition is NOT the appview and was NOT migrated. It is in
    ;; no bundle and referenced by nothing that was replaced, so deleting it would
    ;; have been destruction rather than migration. Pinned so it cannot grow
    ;; silently into a second, unmigrated surface.
    (check! :bpmn-files (:bpmn-files claims)
            (count (filter #(str/ends-with? % ".bpmn") files)))

    ;; the deployed bundle is built from the source in this tree
    (let [w (some-> (slurp* (str APP "/wrangler.jsonc")) strip-jsonc)
          sh (slurp* "shadow-cljs.edn")]
      (if (or (nil? w) (nil? sh))
        (undet! "wrangler.jsonc or shadow-cljs.edn unreadable")
        (let [j (js->clj (.parse js/JSON w) :keywordize-keys false)]
          (check! :wrangler-main (:wrangler-main claims) (get j "main"))
          (check! :declared-vars (:declared-vars claims) (count (get j "vars")))
          (check! :declared-routes (:declared-routes claims) (count (get j "routes")))
          (check! :app-framework (:app-framework claims) (get-in j ["vars" "APP_FRAMEWORK"]))
          (check! :wrangler-display-name (:wrangler-display-name claims)
                  (get-in j ["vars" "APP_DISPLAY_NAME"]))
          ;; the old config served a SvelteKit client dir that never existed here
          (check! :no-stale-assets-binding true (nil? (get j "assets")))
          (check! :sveltekit-compat-flags (:sveltekit-compat-flags claims)
                  (count (filter #{"nodejs_compat" "nodejs_als"}
                                 (or (get j "compatibility_flags") []))))
          (check! :shadow-builds-that-main true
                  (and (str/includes? sh (str ":output-dir \"" (:shadow-output-dir claims) "\""))
                       (str/includes? sh (:shadow-export claims))
                       (str/includes? (get j "main") (str (:shadow-output-dir claims) "/worker.js"))))
          ;; the option that makes "the build succeeded" mean something. Parsed as
          ;; EDN, never grepped: this file's own comments contain the string, so a
          ;; substring search would pass on prose alone -- which is exactly the
          ;; shape of check this option exists to eliminate.
          (check! :warnings-as-errors-in-compiler-options true
                  (let [b (get-in (edn/read-string sh) [:builds :worker])]
                    (and (true? (get-in b [:compiler-options :warnings-as-errors]))
                         (nil? (get-in b [:build-options :warnings-as-errors]))))))))

    ;; The repo asserts two identities at once. Pin both so neither drifts silently.
    (let [r (slurp* "README.edn")]
      (if (nil? r)
        (undet! "README.edn unreadable")
        (check! :readme-edn-role true (str/includes? r (:readme-edn-role claims)))))

    ;; CLAUDE.md now records what is actually in the tree (it described two
    ;; components, neither of which exists here, and a Svelte 5 UI).
    (let [c (slurp* "CLAUDE.md")]
      (if (nil? c)
        (undet! "CLAUDE.md unreadable")
        (check! :claude-md-describes-cljs true
                (and (str/includes? c "shadow-cljs")
                     (str/includes? c "cljs-esm-worker")
                     (str/includes? c "appview/kareyanagi-mcp-component")
                     (str/includes? c "この repository の tree に実在するもの")))))

    ;; The page renders the route TABLE and the env-declared identity rather than
    ;; baked literals -- the defect ADR-0001 records was `routeCount: 0` and
    ;; `title: "Kareyanagi Mcp Component"` beside a config declaring two routes and
    ;; a different name. Asserted STRUCTURALLY and NOT by forbidding a substring:
    ;; a check that a docstring explaining the old defect can fail is a check about
    ;; prose, not about code.
    (let [v (slurp* "src/kareyanagi/view.cljc")
          w (slurp* "src/kareyanagi/worker.cljs")]
      (if (or (nil? v) (nil? w))
        (undet! "view.cljc or worker.cljs unreadable")
        (check! :page-renders-route-table true
                (and (str/includes? v "[{:keys [routes vars mcp-url built-at] :as opts}]")
                     (str/includes? v "(route-rows routes)")
                     (str/includes? v "(:display-name (:identity opts))")
                     (str/includes? w ":routes route/routes")
                     (str/includes? w ":identity (route/identity-of e)")))))))

(let [u @undetermined f @failures]
  (when (seq u)
    (doseq [m u] (println (str "UNDETERMINED\t" m)))
    (println "Refusing to report a pass: the tree could not be read completely.")
    (js/process.exit 2))
  (if (seq f)
    (do (println (str "FAILED\t" (count f) " claim(s): " (str/join ", " (map name f))))
        (js/process.exit 1))
    (do (println "OK\tevery claim in README.md and docs/operator-quickstart.md holds")
        (js/process.exit 0))))
