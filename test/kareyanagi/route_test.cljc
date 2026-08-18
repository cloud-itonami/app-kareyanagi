(ns kareyanagi.route-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [kareyanagi.route :as route]
            [kareyanagi.view :as view]))

(deftest dispatch-page-and-health
  (is (= :page (:action (route/dispatch "GET" "/"))))
  (is (= :health (:action (route/dispatch "GET" "/health"))))
  (is (= :method-not-allowed (:action (route/dispatch "POST" "/health"))))
  (is (= :method-not-allowed (:action (route/dispatch "POST" "/"))))
  (is (= :not-found (:action (route/dispatch "GET" "/nope"))))
  (testing "移行前の SvelteKit は not_found_handling:none で、未知パスは 404"
    (is (= :not-found (:action (route/dispatch "GET" "/_app/meta"))))))

(deftest dispatch-xrpc
  (testing "nsid が在れば中継する"
    (is (= {:action :xrpc :nsid "com.etzhayyim.apps.kareyanagi.listListings"}
           (route/dispatch "POST" "/xrpc/com.etzhayyim.apps.kareyanagi.listListings"))))
  (testing "空の nsid だけが 400。多段は移行前の [...path] と同じく転送する"
    (is (= :bad-request (:action (route/dispatch "POST" "/xrpc/"))))
    (is (= {:action :xrpc :nsid "a/b"} (route/dispatch "POST" "/xrpc/a/b"))))
  (testing "preflight と method"
    (is (= :cors-preflight (:action (route/dispatch "OPTIONS" "/xrpc/x"))))
    (is (= :cors-preflight (:action (route/dispatch "OPTIONS" "/xrpc/"))))
    (is (= :method-not-allowed (:action (route/dispatch "GET" "/xrpc/x"))))
    (is (= "POST, OPTIONS" (:allow (route/dispatch "GET" "/xrpc/x"))))))

(deftest mcp-url-resolution
  (is (= "https://mcp.etzhayyim.com/xrpc/com.etzhayyim.mcp.message"
         (route/mcp-router-url {})))
  (is (= "https://a.example/x"
         (route/mcp-router-url {:AGENTGATEWAY_MCP_ROUTER_URL "https://a.example/x/"})))
  (testing "空白だけの設定は未設定として扱う（移行前の .trim() と同じ）"
    (is (= "https://b.example"
           (route/mcp-router-url {:AGENTGATEWAY_MCP_ROUTER_URL "   "
                                  :MCP_ROUTER_URL "https://b.example"})))))

(deftest unwrap
  (is (= {:ok? true :value {:a 1}} (route/unwrap-mcp {:result {:structuredContent {:a 1}}})))
  (is (= {:ok? true :value {:a 1}} (route/unwrap-mcp {:result {:a 1}})))
  (is (= {:ok? true :value "plain"} (route/unwrap-mcp "plain")))
  (is (false? (:ok? (route/unwrap-mcp {:error {:message "boom"}}))))
  (is (= "boom" (:error (route/unwrap-mcp {:error {:message "boom"}})))))

(deftest identity-comes-from-env-not-from-the-page
  (testing "設定が名乗る名前をそのまま使う（ページに焼かない）"
    (is (= "Wholesale Trade"
           (:display-name (route/identity-of {:APP_DISPLAY_NAME "Wholesale Trade"})))))
  (testing "env が無ければ repo 名に落ちる"
    (is (= "kareyanagi" (:display-name (route/identity-of {}))))
    (is (nil? (:description (route/identity-of {}))))))

(deftest page-shows-the-real-routes
  (testing "ページは route 表と env から描く。0 も固定文字列も焼かない（docs/adr/0001 の欠陥）"
    (let [html (view/render {:css "/*x*/"
                             :identity (route/identity-of
                                        {:APP_DISPLAY_NAME "Wholesale Trade"
                                         :APP_DESCRIPTION "kareyanagi — Dry Goods"
                                         :APP_NANOID "w8p4dsvf"})
                             :routes route/routes
                             :vars [:APP_NANOID :APP_UI_TYPE]
                             :mcp-url "https://mcp.example/x"})]
      (doseq [r route/routes]
        (is (str/includes? html (:route/path r))
            (str (:route/path r) " がページに出ていない")))
      (is (str/includes? html "Wholesale Trade")
          "設定が宣言する表示名が出ていない")
      (is (str/includes? html "w8p4dsvf"))
      (is (str/includes? html "APP_NANOID"))
      (is (str/includes? html "https://mcp.example/x"))
      (testing "移行前のページが焼いていた文言は、もうどこからも出てこない"
        (is (not (str/includes? html "No public route is declared")))
        (is (not (str/includes? html "Kareyanagi Mcp Component")))))))
