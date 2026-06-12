# Eunga-IR Connection Plugin for lr2oraja

[한국어](#한국어) | [English](#english) | [日本語](#日本語)

---

## 한국어

**Eunga-IR(응가IR)** 인터넷 랭킹 서비스([eungatv.com](https://eungatv.com))에 `lr2oraja` / `beatoraja` 플레이어의 점수를 연동 및 전송할 수 있도록 돕는 연동 플러그인 저장소입니다.

### 🚀 최신 업데이트 내용 (v1.3.0)

1. **lr2oraja 게이지 자동 시프트(GAS) 및 게이지 옵션 기록 버그 수정**
   - 게임 클라이언트에서 게이지 자동 시프트(GAS) 기능이 작동할 때, 실제 설정/결과 게이지 유형에 상관없이 무조건 `"GROOVE (-1)"`로 인식되어 전송 및 표기되던 동기화 문제를 수정하였습니다.
   - 플레이어가 최초에 설정한 원본 게이지(`gauge_opt`)와 플레이 후 달성한 최종 결과 게이지(`gauge_result`) 정보를 각각 안전하게 추출하여 기록합니다.
   - 이에 따라 웹 랭킹 상세 페이지 등에서 GAS가 적용된 플레이 기록에 대해 `HARD (GAS)`와 같이 본래 설정한 게이지 뒤에 `(GAS)` 접미사가 깔끔하게 표기됩니다.

2. **결과 화면 ConcurrentModificationException 오류 해결**
   - 순정 `lr2oraja endless dream` 및 `beatoraja` 구동기 환경에서 스코어 전송 시 결과 화면에서 발생하는 쓰레드 동시 수정 예외(`ConcurrentModificationException`) 오류를 런타임 리플렉션 및 동적 랩퍼(Wrapper) 적용을 통해 완벽히 해결하였습니다.
   - 구동기 본체 JAR 패치 없이 플러그인 단독 장착만으로 오류가 완전히 해결됩니다.

3. **인체공학/게이밍 기기 기기 식별명 수집 및 표기 지원**
   - LibGDX 및 JXInput 컨트롤러 인터페이스 리플렉션 조회를 통하여, 아날로그 입력을 사용하는 자석축/래피드 트리거 기계식 키보드가 컨트롤러 장치로 매핑될 때의 정확한 디바이스 모델명(예: `Archon M1 PRO 2 MAX (ART)`)을 정상 수집합니다.
   - 수집된 기기명은 응가TV 인터넷 랭킹 스코어 디테일 화면에 함께 표시됩니다.
   - 이에 맞추어 개인정보처리방침(Privacy Policy) 페이지가 최신화되었습니다.

---

### 주요 기능
- **점수 전송**: 곡 플레이 완료 후 Eunga-IR 서버로 실시간 기록 전송
- **랭킹 및 정보 조회**: 곡 선택 화면 및 리절트 창에서 실시간 랭킹 차트 동기화
- **사용자 검증**: 플레이어 ID 및 API 키(비밀번호 필드에 입력)를 기반으로 닉네임 조회 및 검증 진행
- **최적화**: 가볍고 지연 없는 동작을 보장합니다.

### 설치 방법
1. [Eunga-IR.jar](https://github.com/choco-lily/lr2oraja-lr2ir/raw/main/Eunga-IR.jar) 파일을 다운로드합니다.
2. `lr2oraja` 설치 경로 내의 `ir/` 폴더에 다운로드한 파일을 넣습니다. (기존에 존재하던 타 IR 플러그인 파일은 지우거나 `.bak`로 이름을 변경하세요.)
3. 구동기 설정 화면의 **IR** 탭에서 **EungaIR**을 선택하고 본인의 Player ID와 패스워드 칸에 **응가TV API 키**를 입력합니다.
4. 게임을 실행하여 플레이하면 기록이 전송됩니다.

---

## English

This is the repository for the **Eunga-IR** internet ranking service ([eungatv.com](https://eungatv.com)) connection plugin for `lr2oraja` / `beatoraja`.

### 🚀 What's New in v1.3.0

1. **Fixed Play Gauge Option & GAS (Gauge Auto Shift) bug**
   - Resolved the issue where plays with GAS active were always incorrectly recorded and displayed as `"GROOVE (-1)"` regardless of the actual setting.
   - Properly extracts and transmits the original configuration gauge (`gauge_opt`) and final result gauge (`gauge_result`) independently.
   - Displays options clearly as `HARD (GAS)` or similar if GAS was triggered during the play.

2. **Resolved Result Screen ConcurrentModificationException**
   - Fixed the `ConcurrentModificationException` thread race crash occurring in stock `lr2oraja endless dream` / `beatoraja` result screens during score transmission using reflection and custom list wrappers.
   - Works fully standalone; no launcher executable patching required.

3. **Gaming Keyboard Device Name Detection**
   - Resolves and queries the exact mechanical keyboard device names (such as rapid-trigger, hall effect analog keyboards like `Archon M1 PRO 2 MAX (ART)`) via LibGDX controller reflection.
   - Collected names are stored and displayed in Eunga-IR detailed score rankings.
   - Privacy policies have been updated to reflect this collection.

---

### Features
- **Score Submission**: Live result submissions to the Eunga-IR server.
- **In-Game Leaderboards**: Synchronizes real-time ranking data to the music selector and result screen.
- **Verification**: Logs in securely using Player ID and EungaTV API Key (placed in the password field).

### Installation
1. Download [Eunga-IR.jar](https://github.com/choco-lily/lr2oraja-lr2ir/raw/main/Eunga-IR.jar).
2. Move the downloaded jar into the `ir/` folder of your `lr2oraja` / `beatoraja` directory.
3. Open the launcher, head to the **IR** tab, select **EungaIR**, and fill in your Player ID and **EungaTV API Key** (in the password box).
4. Run the game; scores will sync automatically.

---

## 日本語

`lr2oraja` / `beatoraja` を **Eunga-IR(ウンガIR)** インターネットランキング（[eungatv.com](https://eungatv.com)）に接続する連動プラグインのリポジトリです。

### 🚀 更新履歴 (v1.3.0)

1. **GAS (Gauge Auto Shift) およびゲージオプション判定バグの修正**
   - GAS有効時に設定・リザルトゲージにかかわらず `"GROOVE (-1)"` として記録・表示されてしまう不具合를 修正しました。
   - プレイヤーが最初に設定したゲージオプション(`gauge_opt`)と最終リザルトゲージ情報(`gauge_result`)をそれぞれ個別に正しく抽出・送信します。
   - 詳細パネルにおいて、元々HARDゲージでプレイしGASが適用された場合は `HARD (GAS)` のように分かりやすく表示されます。

2. **結果画面での ConcurrentModificationException エラーの修正**
   - 標準の `lr2oraja endless dream` / `beatoraja` ランチャー環境で、スコア送信時にリザルト画面で発生していたスレッド競合エラー（`ConcurrentModificationException`）を、実行時リフレクションおよび動的ラッパー（Wrapper）の適用により完全に解決しました。

3. **ゲーミングデバイス名の自動検出と表示対応**
   - 磁気軸・ラピッドトリガーを搭載したメカニカルキーボードなどのアナログ入力対応デバイス（例: `Archon M1 PRO 2 MAX (ART)`）がLibGDXコントローラーインターフェースにマッピングされる際、正確なデバイスモデル名を自動取得します。
   - 取得されたキーボード名はウンガTVランキングの詳細画面に記録・表示されます。
   - これに合わせてプライバシーポリシーが改定されました。

---

### 主な機能
- **スコア送信**: プレイ終了後、Eunga-IR サーバーへ記録を自動送信します。
- **ランキング同期**: 選曲画面およびリザルト画面でのリアルタイムランキング同期に対応。
- **プレイヤー認証**: 登録されたプレイヤー ID と **ウンガTV APIキー**（パスワード入力欄に入力）を用いて認証を行います。

### 導入方法
1. [Eunga-IR.jar](https://github.com/choco-lily/lr2oraja-lr2ir/raw/main/Eunga-IR.jar) をダウンロードします。
2. `lr2oraja` / `beatoraja` のインストールフォルダ内にある `ir/` フォルダにファイルを配置します。
3. ウンガ設定画面の **IR** タブから **EungaIR** を選択し、ご自身のプレイヤー ID と **ウンガTV APIキー** を入力します。
4. ゲームを起動してプレイすると、インターネットランキングに自動登録されます。
