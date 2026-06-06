# BMS-IR Connection Plugin for lr2oraja

[한국어](#한국어) | [English](#english) | [日本語](#日本語)

---

## 한국어

공식 LR2IR 서비스가 종료된 이후, 부활한 **BMS-IR** 랭킹 서비스에서 `lr2oraja` / `beatoraja` 플레이어의 점수를 연동 및 전송할 수 있도록 돕는 커스텀 IR 연결 플러그인입니다.

### 🚀 최신 업데이트 내용 (v1.2.9)

최신 버전에서는 공식 플러그인(`bms_ir_ed_0.0.21.jar`)의 새로운 점수 파라미터(`clear_type`, `gauge_type`, `gauge_option`)를 온전히 지원하며, 플레이어가 직접 전송 식별 모드를 구성할 수 있도록 지원합니다.

1. **식별 모드 설정 파일 (`bmsir-spoof.txt`) 지원**
   - 플러그인이 로드되면 같은 폴더에 자동으로 `bmsir-spoof.txt` 설정 파일이 생성됩니다.
   - 플레이어는 파일 내의 설정을 수정하여 세 가지 모드 중 하나를 선택할 수 있습니다:
     - **`lr2`**: 이전과 동일하게 순정 LR2 클라이언트로 위장하여 전송합니다. (User-Agent: `LR2`, 상세 해시/식별자 미전송)
     - **`ed`**: Endless Dream 버전의 lr2oraja로 식별 정보를 전송합니다. (User-Agent: `BmsIRUpload/100130`, 클래스 해시 및 상세 플레이어 상태값 전송)
     - **`vanilla`**: 순정 lr2oraja / beatoraja 버전으로 식별 정보를 전송합니다. (User-Agent: `BmsIRUpload/100130`, 클래스 해시 및 상세 플레이어 상태값 전송)
2. **0.0.21 규격 게이지 및 클리어 상태 코드 지원**
   - 공식 0.0.21 버전에 추가된 플레이 통계 필드인 `clear_type` (beatoraja 내부 클리어 타입 번호), `gauge_type` (인게임 게이지 종류 번호), `gauge_option` (매핑된 LR2 게이지 코드) 파라미터를 BMS-IR 인터넷 랭킹 서버로 함께 전송하여 상세한 기록 정보가 기록 웹사이트 상에 정확하게 반영되도록 개선했습니다.

---

### 🚀 이전 업데이트 내용 (v1.2.8)

공식 플러그인(`bms_ir_0.0.19.jar`)의 핵심 기능들을 이식하면서, 서버 측의 클라이언트 제한을 우회하기 위한 **LR2 위장(Spoofing)** 기능과 편의성이 대폭 강화되었습니다.

1. **순정 구동기 랜덤(RANDOM) 완벽 지원 (LR2 호환 난수 시드 자동 분석)**
   - 순정 구동기(시드 범위 `0 ~ 16777215`)에서 RANDOM 또는 ROTATE 옵션으로 플레이 시, 플러그인 내부에서 자동으로 생성된 라인 배치를 분석하여 그에 대응하는 **LR2 호환 32비트 난수 시드(0 ~ 32766)**를 찾아 매핑해 줍니다.
   - 이에 따라, 개조되지 않은 순정 lr2oraja/beatoraja 클라이언트에서도 난수 제한 없이 점수를 정상 전송할 수 있습니다.
   - 로그인 시 9키 난수 캐시를 백그라운드(Daemon Thread)에서 미리 생성(Prewarm)하여 게임 플레이 도중 렉이나 지연이 발생하지 않도록 최적화했습니다.

2. **인게임 라이벌(Rivals) 기능 활성화**
   - 기존 플러그인은 라이벌 기능이 비활성화되어 인게임에서 점수가 연동되지 않았습니다.
   - 새 버전에서는 마이페이지 HTML(`search.cgi?mode=mypage`)을 정규 표현식으로 직접 파싱하여 플레이어가 등록해 둔 라이벌 정보(ID, 닉네임)를 연동합니다.
   - 인게임 대기실 및 선곡 화면에서 등록된 라이벌들의 실시간 기록 연동 및 비교가 가능합니다.

3. **LR2 완벽 우회 위장 (Client Spoofing)**
   - HTTP 요청 헤더의 User-Agent를 `LR2`로 강제 고정합니다.
   - 점수 전송 시 구동기를 식별하는 값(`client_kind`, `client_version`, `client_hash`, `plugin_hash` 등)을 제외하여 서버 측에서 플레이어를 정품 LR2 클라이언트로 인식하게 합니다.

4. **MAX Clear (Clear Type 10) 매핑 개선**
   - 모든 노트 판정이 PGREAT인 플레이(All PG)의 경우, 리절트 조회 시 단순 퍼펙트가 아닌 beatoraja 전용 **MAX Clear (Clear Type 10)**로 매핑하여 판정 기록을 정밀하게 보존합니다.

5. **이중 전송 버그 수정**
   - 일부 클라이언트 환경에서 하나의 리절트가 서버에 두 번 중복 등록되는 전송 프로토콜 이슈를 해결했습니다.

---

### 주요 기능
- **점수 전송**: 곡 플레이 완료 후 BMS-IR 서버(`score.cgi`)로 실시간 기록 전송
- **랭킹 및 정보 조회**: 곡 선택 화면 및 리절트 창에서 실시간 랭킹 차트 동기화
- **사용자 검증**: 플레이어 ID 및 패스워드를 기반으로 닉네임 조회 및 검증 진행
- **LR2 규격 준수**: 난수 시드(Seed) 자동 분석, 게이지/랜덤 옵션 매핑 규격 적용
- **최적화**: 미지원 API 기능(테이블 데이터 등) 호출 시 콘솔 경고 로그 억제 및 가벼운 동작 보장 (로컬 DB 접근 배제로 인한 데이터베이스 락 방지)

### 설치 방법
1. [BMS-IR.jar](https://github.com/choco-lily/lr2oraja-lr2ir/raw/main/BMS-IR.jar) 파일을 다운로드합니다.
2. `lr2oraja` 설치 경로 내의 `ir/` 폴더에 다운로드한 파일을 넣습니다. (기존에 존재하던 `bms_ir_x.x.x.jar` 등 공식 플러그인 파일은 지우거나 `.bak`로 이름을 변경하세요.)
3. 구동기 설정 화면의 **IR** 탭에서 **BMS-IR**을 선택하고 본인의 Player ID와 패스워드를 입력합니다.
4. 게임을 실행하여 플레이하면 기록이 전송됩니다.

---

## English

This is a custom IR connection plugin that enables using `lr2oraja` / `beatoraja` with the resurrected **BMS-IR** ranking service after the official LR2IR service was shut down.

### 🚀 What's New in v1.2.9

The latest release introduces support for the new play parameters from the official version `0.0.21` (`clear_type`, `gauge_type`, `gauge_option`) and allows players to customize their client identification spoofing modes.

1. **Configurable Spoofing Mode via `bmsir-spoof.txt`**
   - On plugin startup, a configuration template `bmsir-spoof.txt` is automatically created in the plugin directory.
   - Players can edit this file to select one of the following modes:
     - **`lr2`**: Spoofs as classic LR2. (User-Agent: `LR2`, hides beatoraja details and skips hashing verification parameters)
     - **`ed`**: Spoofs as Endless Dream lr2oraja. (User-Agent: `BmsIRUpload/100130`, submits class verification hashes and play metadata)
     - **`vanilla`**: Spoofs as vanilla lr2oraja. (User-Agent: `BmsIRUpload/100130`, submits class verification hashes and play metadata)
2. **Modern Clear/Gauge Statistics (0.0.21 Specification)**
   - Submits `clear_type` (beatoraja ClearType ID), `gauge_type` (beatoraja GaugeType ID), and `gauge_option` (mapped LR2 gauge option code) to the BMS-IR server, enabling detailed gauge/clear stats to be rendered on the rankings web portal.

---

### 🚀 What's New in v1.2.8

The v1.2.8 release ports features from the official plugin (`bms_ir_0.0.19.jar`) while implementing complete **LR2 Client Spoofing** to bypass server-side client restrictions.

1. **Unmodified Client RANDOM Support (LR2 Random Seed Resolution)**
   - When playing with RANDOM/ROTATE options on unmodified clients (seed range `0 to 16777215`), the plugin analyzes the generated lane configuration in-memory and resolves it to a matching **LR2-compatible 32-bit seed (0 to 32766)**.
   - Standard, unmodified lr2oraja/beatoraja clients can now submit RANDOM scores without getting blocked.
   - Resolves Pop'n 9K seeds asynchronously (via Daemon Thread) upon successful login to prevent performance hiccups during play.

2. **In-game Rival Feature Support**
   - Previously, the rival list was disabled, making rival comparison non-functional.
   - The plugin now parses your registered rivals list directly from the player's mypage HTML using regular expressions.
   - You can now compare and display real-time rival scores on the music selector and result screen.

3. **Complete LR2 Spoofing**
   - Overrides the HTTP request header User-Agent to `LR2`.
   - Omits client classification parameters (`client_kind`, `client_version`, `client_hash`, `plugin_hash`, etc.) to register plays as standard, official `LR2` scores.

4. **MAX Clear (Clear Type 10) Mapping**
   - Automatically maps All PG plays to beatoraja's **MAX Clear (Clear Type 10)** in leaderboards, keeping clear types precise.

5. **Double-Upload Prevention**
   - Resolved double-submission score upload bugs occurring in specific client environments.

---

### Features
- **Score Submission**: Automatically transmits play records to the BMS-IR server (`score.cgi`) upon play completion.
- **Leaderboard Integration**: Synchronizes and displays real-time chart rankings on the music selector and result screen.
- **Player Authentication**: Verifies the inputted Player ID and retrieves the actual registered nickname.
- **LR2 Specification Matching**: Maps options/gauges and maps random seeds to matching LR2 limits.
- **Log Optimization & Robustness**: Silences console warnings for unsupported hooks. Operates entirely in-memory (no local DB connections to prevent SQLite database lock crashes).

### Installation
1. Download [BMS-IR.jar](https://github.com/choco-lily/lr2oraja-lr2ir/raw/main/BMS-IR.jar).
2. Move the downloaded JAR into the `ir/` directory of your `lr2oraja`/`beatoraja` installation path. (Delete or rename official plugins like `bms_ir_x.x.x.jar` to `.bak`).
3. Open the game configuration launcher, go to the **IR** tab, select **BMS-IR**, and input your Player ID and password.
4. Launch the game and play; your scores will be uploaded automatically.

---

## 日本語

公式の LR2IR サービスが終了した後、復活した **BMS-IR** ランクサービスで `lr2oraja` / `beatoraja` プレイヤーの プレイ記録を連動・送信できるようにするカスタム IR 接続プラグインです。

### 🚀 更新履歴 (v1.2.9)

最新バージョンでは、公式の最新プラグイン（`bms_ir_ed_0.0.21.jar`）の新しいプレイ統計パラメータ（`clear_type`, `gauge_type`, `gauge_option`）をサポートし、プレイヤー自身で送信識別モードを設定できる機能を追加しました。

1. **識別モード設定ファイル (`bmsir-spoof.txt`) の導入**
   - プラグインの起動時に、JARファイルと同じディレクトリ内に `bmsir-spoof.txt` 設定ファイルが自動的に生成されます。
   - プレイヤーは設定ファイルを編集することで、次の3つのモードから接続動作を選択できます：
     - **`lr2`**: 従来どおり純正の LR2 クライアントに偽装してスコアを送信します。（User-Agent: `LR2`、詳細な識別子やクラスハッシュは送信されません）
     - **`ed`**: Endless Dream版 lr2oraja として接続を識別させます。（User-Agent: `BmsIRUpload/100130`、クラスハッシュやメタデータを送信）
     - **`vanilla`**: 純正 lr2oraja として接続を識別させます。（User-Agent: `BmsIRUpload/100130`、クラスハッシュやメタデータを送信）
2. **0.0.21 仕様のクリア・ゲージ統計コード送信対応**
   - 新規パラメータ `clear_type`（beatoraja内部クリアタイプID）、`gauge_type`（インゲームのゲージ種類ID）、`gauge_option`（LR2互換にマッピングされたゲージオプションコード）をBMS-IRサーバーへ送信するように更新され、詳細な統計情報をネットランキング側で正確に反映できるようになりました。

---

### 🚀 更新履歴 (v1.2.8)

以前のバージョンでは、公式プラグイン（`bms_ir_0.0.19.jar`）の主要機能を取り込みつつ、サーバー側のクライアント制限を回避するための **LR2偽装（Client Spoofing）** 機能と利便性が大幅に強化されました。

1. **未改造クライアントでのランダム（RANDOM）完全対応 (LR2互換乱数シード自動変換)**
   - 未改造の標準クライアント（乱数シード範囲 `0 〜 16777215`）で RANDOM または ROTATE オプションを適用してプレイする際、プラグイン内で自動的にレーン配置を逆解析し、対応する **LR2互換の32ビット乱数シード（0 〜 32766）** へ動的にマッピングします。
   - これにより、改造版以外の純粋な標準クライアントでもエラーにならずにスコアが送信されます。
   - ログイン時に9鍵乱数キャッシュをバックグラウンド（デモンスレッド）で事前ロード（Prewarm）し、プレイ中のフレームレート低下を防止しています。

2. **インゲームライバル（Rivals）機能の活性化**
   - これまでのプラグインでは動作しなかったライバル機能を、マイページHTML（`search.cgi?mode=mypage`）をパースする仕組みを導入することで解決しました。
   - ゲーム内のライバルリスト、選曲画面でのスコア比較、リザルト画面での競合記録のリアルタイム連動に対応しました。

3. **LR2への完全ウラ偽装 (Client Spoofing)**
   - 送信リクエストの User-Agent を `LR2` に強制的に書き換えます。
   - `client_kind` や `client_version`、各種クライアントハッシュ等のメタデータをサーバーへの送信フォームから除外することで、サーバー側からは公式の `LR2` 接続として判定させます。

4. **MAX Clear (Clear Type 10) 判定マッピング対応**
   - 全てのノート判定が PGREAT（All PG）の際、通常のパーフェクトクリアではなく、beatoraja専用の **MAX Clear (Clear Type 10)** としてリファクタリングしてマッピングを行います。

5. **重複送信バグの解消**
   - 特定の環境で1回のプレイに対してスコアが2回送信されていた問題を修正しました。

---

### 主な機能
- **スコア送信**: プレイ終了後、BMS-IR サーバー（`score.cgi`）へ記録を自動送信します。
- **ランキング同期**: 選曲画面およびリザルト画面でのリアルタイムランキング同期に対応。
- **プレイヤー認証**: 登録されたプレイヤー ID を検証し、自動的にニックネームを取得してログインを行います。
- **LR2 仕様準拠**: 乱数シードの最大値制限（`32766`）、ゲージおよび各種オプションの LR2 互換マッピングを実装。
- **最適化と軽量化**: 未サポート機能のコンソール警告ログを抑制。ローカルのSQLiteデータベースへの直接接続を行わないため、競合によるデータベースロック死を防ぎます。

### 導入方法
1. [BMS-IR.jar](https://github.com/choco-lily/lr2oraja-lr2ir/raw/main/BMS-IR.jar) をダウンロードします。
2. `lr2oraja` または `beatoraja` のインストールフォルダ内にある `ir/` フォルダにファイルを配置します。（公式の `bms_ir_x.x.x.jar` 等は削除するか `.bak` にリネームしてロードを防いでください。）
3. ランチャー設定画面の **IR** タブから **BMS-IR** を選択し、ご自身のプレイヤー ID とパスワードを入力します。
4. ゲームを起動してプレイすると、インターネットランキングに自動登録されます。
