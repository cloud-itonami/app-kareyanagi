# etzhayyim-project-kareyanagi

カビ撲滅プラットフォーム (kareyanagi.etzhayyim.com)。カビセンサー IoT データ収集、空間分布マッピング (maps.etzhayyim.com 連携)、リスク予測、撲滅アクション管理。

## Components

| Component | Folder | nanoid | 役割 |
|---|---|---|---|
| kareyanagi-api | `etzhayyim-wasm-kareyanagi-mcr736od` | mcr736od | カビセンサーデータ収集 + リスク分析 + XRPC API |
| kareyanagi-ui | `etzhayyim-wasm-kareyanagi-ui-kpat4bp7` | kpat4bp7 | ダッシュボード UI + maps.etzhayyim.com 空間連携ビュー |

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  kareyanagi-ui (kpat4bp7)                               │
│  Svelte 5 + MapLibre (maps.etzhayyim.com tile 共有)           │
│  カビ分布ヒートマップ / センサー状態 / アラート          │
└──────────────────────┬──────────────────────────────────┘
                       │ XRPC
┌──────────────────────▼──────────────────────────────────┐
│  kareyanagi-api (mcr736od)                              │
│  センサーデータ収集 / リスクスコア算出 / 撲滅タスク管理  │
│  AT channel: at://kareyanagi-{nanoid}                   │
├─────────────────────────────────────────────────────────┤
│  External Dependencies                                  │
│  ├─ maps.etzhayyim.com (uqpel6i6) — 空間検索 + geolocation   │
│  ├─ murakumo.etzhayyim.com — LLM リスク分析                   │
│  └─ IoT sensor gateway — MQTT/HTTP ingest               │
└─────────────────────────────────────────────────────────┘
```

## Domain Model

### カビセンサー (MoldSensor)

| Field | Type | 説明 |
|---|---|---|
| `sensorId` | String | nanoid |
| `location_lat` | Float64 | 緯度 |
| `location_lng` | Float64 | 経度 |
| `location_h3` | String | H3 index (maps.etzhayyim.com 互換) |
| `humidity` | Float64 | 湿度 (%) |
| `temperature` | Float64 | 温度 (°C) |
| `mold_spore_count` | Float64 | 胞子数 (CFU/m³) |
| `mold_species` | String | 検出カビ種 |
| `risk_score` | Float64 | リスクスコア (0.0–1.0) |
| `measured_at` | Timestamp | 計測日時 |
| `org_id` | String | RLS |
| `user_id` | String | RLS |
| `actor_id` | String | RLS |

### カビ分布レコード (MoldDistribution)

| Field | Type | 説明 |
|---|---|---|
| `distribution_id` | String | nanoid |
| `h3_index` | String | H3 cell (resolution 9) |
| `avg_risk_score` | Float64 | セル内平均リスク |
| `sensor_count` | Int32 | セル内センサー数 |
| `dominant_species` | String | 最頻出カビ種 |
| `updated_at` | Timestamp | 最終更新 |
| `org_id` | String | RLS |
| `user_id` | String | RLS |
| `actor_id` | String | RLS |

### 撲滅タスク (EradicationTask)

| Field | Type | 説明 |
|---|---|---|
| `task_id` | String | nanoid |
| `sensorId` | String | 対象センサー |
| `h3_index` | String | 対象エリア |
| `priority` | String | critical/high/medium/low |
| `status` | String | pending/in_progress/completed/verified |
| `method` | String | 除カビ手法 (chemical/uv/ventilation/dehumidify) |
| `assigned_to` | String | 担当者 actor_id |
| `created_at` | Timestamp | 作成日時 |
| `completed_at` | Timestamp | 完了日時 |
| `org_id` | String | RLS |
| `user_id` | String | RLS |
| `actor_id` | String | RLS |

## maps.etzhayyim.com 連携

| 連携パターン | 方式 | 詳細 |
|---|---|---|
| **空間検索** | XRPC → `maps_ui.search_resources` | カビ発生地点周辺のリソース検索 |
| **グラフ索引** | XRPC → `maps_ui.graph_index_search_result` | カビセンサー位置をエンティティグラフに登録 |
| **ヒートマップ overlay** | GeoJSON dataset → `maps_collection.store_map_dataset` | カビ分布を GeoJSON レイヤーとして maps に公開 |
| **近傍探索** | XRPC → `maps_ui.graph_neighbors` | センサー近傍の建物・施設を取得 |
| **タイル共有** | `MAP_STYLE_URL` 共通 | 同一 OpenFreeMap タイル使用 |

## KV Buckets

| Bucket | Component | Store name |
|---|---|---|
| `kareyanagi-state` | etzhayyim-wasm-kareyanagi-mcr736od | `default` |
| `kareyanagi-ui-state` | etzhayyim-wasm-kareyanagi-ui-kpat4bp7 | `default` |

## MCP Tools

### kareyanagi-api (mcr736od) — `/etzhayyim.kareyanagi.v1.KareyangiService`

- `kareyanagi.ingest_sensor_data` / `IngestSensorData` — センサーデータ受信 + リスクスコア算出
- `kareyanagi.get_sensor` / `GetSensor` — センサー情報取得
- `kareyanagi.list_sensors` / `ListSensors` — センサー一覧 (paginated, H3 フィルタ可)
- `kareyanagi.get_distribution` / `GetDistribution` — H3 セル別カビ分布取得
- `kareyanagi.list_distributions` / `ListDistributions` — 分布一覧 (リスクスコア降順)
- `kareyanagi.create_eradication_task` / `CreateEradicationTask` — 撲滅タスク作成
- `kareyanagi.update_eradication_task` / `UpdateEradicationTask` — タスク状態更新
- `kareyanagi.list_eradication_tasks` / `ListEradicationTasks` — タスク一覧 (status フィルタ可)
- `kareyanagi.publish_distribution_to_maps` / `PublishDistributionToMaps` — カビ分布 GeoJSON を maps.etzhayyim.com に公開
- `kareyanagi.risk_analysis` / `RiskAnalysis` — murakumo LLM でリスク分析レポート生成

## API Endpoints

- kareyanagi-api: `https://mcr736od.etzhayyim.com/xrpc`
- kareyanagi-ui: `https://kpat4bp7.etzhayyim.com`

## Entity Graph Integration

Maps vocab prefixes に加え kareyanagi 固有 prefix を登録:

| Prefix | Namespace | 用途 |
|---|---|---|
| `mold:` | `https://etzhayyim.com/ns/mold/` | カビ種、胞子数、リスク |
| `sensor:` | `https://etzhayyim.com/ns/sensor/` | IoT センサーノード |
| `eradication:` | `https://etzhayyim.com/ns/eradication/` | 撲滅タスク |

## IoT センサー仕様

### 対応プロトコル

| プロトコル | 用途 |
|---|---|
| MQTT (TLS) | リアルタイムセンサーデータ push |
| HTTP POST | バッチデータ ingest |
| BLE beacon | 近距離センサー発見 |

### 推奨センサーハードウェア

| センサー種別 | 計測項目 |
|---|---|
| 温湿度センサー (SHT40/BME680) | 温度、湿度、気圧、VOC |
| 胞子カウンター (Laser particle) | 空中胞子密度 (CFU/m³) |
| 表面カビ検出 (UV-fluorescence) | 表面カビコロニー検出 |
| CO2 センサー (SCD41) | CO2 濃度 (換気指標) |

## Smoke Test

```bash
curl https://mcr736od.etzhayyim.com/health
curl https://kpat4bp7.etzhayyim.com/health
curl -X POST https://mcr736od.etzhayyim.com/xrpc/etzhayyim.kareyanagi.v1.KareyangiService/ListSensors \
  -H "Content-Type: application/json" -d '{"offset":0,"limit":10}'
curl -X POST https://mcr736od.etzhayyim.com/xrpc/etzhayyim.kareyanagi.v1.KareyangiService/ListDistributions \
  -H "Content-Type: application/json" -d '{"offset":0,"limit":10}'
```
