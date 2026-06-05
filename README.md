# BMS-IR Connection Plugin for lr2oraja

[한국어](#한국어) | [English](#english) | [日本語](#日本語)

---

## 한국어

공식 LR2IR 서비스가 종료된 이후, 부활한 **BMS-IR** 랭킹 서비스에서 `lr2oraja` 플레이어의 점수를 연동 및 전송할 수 있도록 돕는 커스텀 IR 연결 플러그인입니다.

> [!WARNING]
> **중요**: 이 플러그인은 LR2 시드 범위(`0 ~ 32766`)를 생성하도록 개조된 `lr2oraja` 클라이언트([choco-lily/lr2oraja-endlessdream](https://github.com/choco-lily/lr2oraja-endlessdream))에서만 정상 작동합니다. 순정 구동기(시드 범위 `0 ~ 16777215`)의 전송 요청은 클라이언트 단에서 차단됩니다.

### 주요 기능
- **점수 전송**: 곡 플레이 완료 후 BMS-IR 서버(`score.cgi`)로 실시간 기록 전송 (시드 값 검증 포함)
- **랭킹 및 정보 조회**: 곡 선택 화면 및 리절트 창에서 실시간 랭킹 차트 동기화
- **사용자 검증**: 플레이어 ID 및 패스워드를 기반으로 닉네임 조회 및 검증 진행
- **LR2 규격 준수**: 난수 시드(Seed) 최댓값 제한(`32766`), 게이지/랜덤 옵션 매핑 규격 적용
- **최적화**: 미지원 API 기능(테이블 데이터, 라이벌 정보 등) 호출 시 콘솔 경고 로그 억제

### 설치 방법
1. [BMS-IR.jar](https://github.com/choco-lily/lr2oraja-lr2ir/raw/main/BMS-IR.jar) 파일을 다운로드합니다.
2. `lr2oraja` 설치 경로 내의 `ir/` 폴더에 다운로드한 파일을 넣습니다.
3. 구동기 설정 화면의 **IR** 탭에서 **BMS-IR**을 선택하고 본인의 Player ID와 패스워드를 입력합니다.
4. 게임을 실행하여 플레이하면 기록이 전송됩니다.

---

## English

This is a custom IR connection plugin that enables using `lr2oraja` with the resurrected **BMS-IR** ranking service after the official LR2IR service was shut down.

> [!WARNING]
> **IMPORTANT**: This plugin only works with the modified `lr2oraja` client ([choco-lily/lr2oraja-endlessdream](https://github.com/choco-lily/lr2oraja-endlessdream)) configured for the LR2 seed range (`0 to 32766`). Requests from unmodified clients (which generate seeds in `0 to 16777215`) will be blocked on the client side.

### Features
- **Score Submission**: Automatically transmits play records to the BMS-IR server (`score.cgi`) upon play completion (with seed range validation).
- **Leaderboard Integration**: Synchronizes and displays real-time chart rankings on the music selector and result screen.
- **Player Authentication**: Verifies the inputted Player ID and retrieves the actual registered nickname.
- **LR2 Specification Matching**: Maps options/gauges and limits random seed generation to matching LR2 limits (`32766`).
- **Log Optimization**: Silences console warnings for unsupported hooks (e.g., table data, rival sync).

### Installation
1. Download [BMS-IR.jar](https://github.com/choco-lily/lr2oraja-lr2ir/raw/main/BMS-IR.jar).
2. Move the downloaded JAR into the `ir/` directory of your `lr2oraja` installation path.
3. Open the game configuration launcher, go to the **IR** tab, select **BMS-IR**, and input your Player ID and password.
4. Launch the game and play; your scores will be uploaded automatically.

---

## 日本語

公式の LR2IR サービスが終了した後、復活した **BMS-IR** ランクサービスで `lr2oraja` プレイヤー의 プレイ記録を連動・送信できるようにするカスタム IR 接続プラグインです。

> [!WARNING]
> **重要**: このプラグインは、LR2の乱数シード範囲（`0 〜 32766`）で動作するように改造された `lr2oraja` クライアント（[choco-lily/lr2oraja-endlessdream](https://github.com/choco-lily/lr2oraja-endlessdream)）専用です。未改造の標準クライアント（シード範囲 `0 〜 16777215`）からのスコア送信要求はクライアント側で遮断されます。

### 主な機能
- **スコア送信**: プレイ終了後、BMS-IR サーバー（`score.cgi`）へ記録を自動送信します（シード値範囲の事前検証含む）。
- **ランキング同期**: 選曲画面およびリザルト画面でのリアルタイムランキング同期に対応。
- **プレイヤー認証**: 登録されたプレイヤー ID を検証し、自動的にニックネームを取得してログインを行います。
- **LR2 仕様準拠**: 乱数シードの最大値制限（`32766`）、ゲージおよび各種オプションの LR2 互換マッピングを実装。
- **警告抑制**: 未サポートの機能（テーブルデータやライバル一覧など）が呼び出された際のコンソール警告ログを抑制。

### 導入方法
1. [BMS-IR.jar](https://github.com/choco-lily/lr2oraja-lr2ir/raw/main/BMS-IR.jar) をダウンロードします。
2. `lr2oraja` のインストールフォルダ内にある `ir/` フォルダにファイルを配置します。
3. ランチャー設定画面の **IR** タブから **BMS-IR** を選択し、ご自身のプレイヤー ID とパスワードを入力します。
4. 게임を起動してプレイすると、インターネットランキングに自動登録されます。
