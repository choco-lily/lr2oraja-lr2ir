# Eunga-IR Connection Plugin for lr2oraja

[한국어](#한국어) | [English](#english) | [日本語](#日本語)

---

## 한국어

**Eunga-IR(응가IR)** 인터넷 랭킹 서비스([eungatv.com](https://eungatv.com))에 `lr2oraja` / `beatoraja` 플레이어의 점수를 연동 및 전송할 수 있도록 돕는 연동 플러그인과 공식 BMS-IR 플러그인 위장(스푸핑) 도구 저장소입니다.

### 🚀 최신 업데이트 내용 (v1.2.9)

1. **결과 화면 ConcurrentModificationException 오류 해결**
   - 순정 `lr2oraja endless dream` 및 `beatoraja` 구동기 환경에서 스코어 전송 시 결과 화면에서 발생하는 쓰레드 동시 수정 예외(`ConcurrentModificationException`) 오류를 런타임 리플렉션 및 동적 랩퍼(Wrapper) 적용을 통해 완벽히 해결하였습니다.
   - 구동기 본체 JAR 패치 없이 플러그인 단독 장착만으로 오류가 완전히 해결됩니다.

2. **인체공학/게이밍 기기 기기 식별명 수집 및 표기 지원**
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

### 🛠️ 공식 BMS-IR 플러그인 위장 도구 (Client Spoofing Tool)

공식 BMS-IR 플러그인(`bms_ir_ed_0.0.27.jar` 등)을 순정 LR2로 자동 위장(스푸핑) 처리하여 전송 제약을 우회하기 위한 CLI 도구입니다.

#### 사용 방법
1. **드래그 앤 드롭 방식**:
   - 위장할 대상 공식 `.jar` 플러그인 파일을 [spoof_jar.bat](file:///d:/Programming/lr2oraja_to_lr2ir/LR2IR_plugin/spoof_jar.bat) 파일 위로 드래그 앤 드롭합니다.
2. **콘솔 명령 방식**:
   - 터미널을 열고 아래 명령을 실행합니다:
     ```bash
     python spoof_jar.py <path_to_official_bms_ir_jar>
     ```
- 변환이 완료되면 원본 파일은 `.bak` 백업 파일로 저장되며, 원본 이름의 파일에 패치된 클래스 바이트가 덮어씌워집니다.

---

## English

This is the repository for the **Eunga-IR** internet ranking service ([eungatv.com](https://eungatv.com)) connection plugin for `lr2oraja` / `beatoraja`, along with official BMS-IR plugin spoofing utility scripts.

### 🚀 What's New in v1.2.9

1. **Resolved Result Screen ConcurrentModificationException**
   - Fixed the `ConcurrentModificationException` thread race crash occurring in stock `lr2oraja endless dream` / `beatoraja` result screens during score transmission using reflection and custom list wrappers.
   - Works fully standalone; no launcher executable patching required.

2. **Gaming Keyboard Device Name Detection**
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

### 🛠️ Official BMS-IR Plugin Spoofing Tool

A CLI utility designed to patch official BMS-IR plugins (e.g., `bms_ir_ed_0.0.27.jar`) to make them identify as standard LR2 clients, bypassing restrictions.

#### How to Use
1. **Drag and Drop**:
   - Drag and drop your official `.jar` file directly onto [spoof_jar.bat](file:///d:/Programming/lr2oraja_to_lr2ir/LR2IR_plugin/spoof_jar.bat).
2. **Terminal CLI**:
   - Run the command below:
     ```bash
     python spoof_jar.py <path_to_official_bms_ir_jar>
     ```
- Once completed, the original jar is backed up as `.bak`, and the patched jar replaces the original.

---

## 日本語

`lr2oraja` / `beatoraja` を **Eunga-IR(ウンガIR)** インターネットランキング（[eungatv.com](https://eungatv.com)）に接続する連動プラグイン、および公式のBMS-IRプラグイン偽装（スプーフィング）ツールスクリプトのリポジトリです。

### 🚀 更新履歴 (v1.2.9)

1. **結果画面での ConcurrentModificationException エラーの修正**
   - 標準の `lr2oraja endless dream` / `beatoraja` ランチャー環境で、スコア送信時にリザルト画面で発生していたスレッド競合エラー（`ConcurrentModificationException`）を、実行時リフレクションおよび動的ラッパー（Wrapper）の適用により完全に解決しました。

2. **ゲーミングデバイス名の自動検出と表示対応**
   - 磁気軸・ラピッドトリガーを搭載したメカニカルキーボードなどのアナログ入力対応デバイス（例: `Archon M1 PRO 2 MAX (ART)`）がLibGDXコントローラーインターフェースにマッピングされる際、正確なデバイスモデル名を自動取得します。
   - 取得されたキーボード名はウンガTVインターネットランキングの詳細画面に記録・表示されます。
   - これに合わせてプライバシーポリシーが改定されました。

---

### 主な機能
- **スコア送信**: プレイ終了後、Eunga-IR サーバーへ記録を自動送信します。
- **ランキング同期**: 選曲画面およびリザルト画面でのリアルタイムランキング同期に対応。
- **プレイヤー認証**: 登録されたプレイヤー ID と **ウンガTV APIキー**（パスワード入力欄に入力）を用いて認証を行います。

### 導入方法
1. [Eunga-IR.jar](https://github.com/choco-lily/lr2oraja-lr2ir/raw/main/Eunga-IR.jar) をダウンロードします。
2. `lr2oraja` / `beatoraja` のインストールフォルダ内にある `ir/` フォルダにファイルを配置します。
3. ランチャー設定画面の **IR** タブから **EungaIR** を選択し、ご自身のプレイヤー ID と **ウンガTV APIキー** を入力します。
4. ゲームを起動してプレイすると、インターネットランキングに自動登録されます。

---

### 🛠️ 公式BMS-IRプラグイン偽装ツール

公式BMS-IRプラグイン（`bms_ir_ed_0.0.27.jar`など）を純正LR2として自動偽装処理し、クライアント制限を回避するためのコマンドラインユーティリティです。

#### 使用方法
1. **ドラッグ＆ドロップ**:
   - 偽装対象の公式 `.jar` ファイルを [spoof_jar.bat](file:///d:/Programming/lr2oraja_to_lr2ir/LR2IR_plugin/spoof_jar.bat) の上へドラッグ＆ドロップします。
2. **コマンドライン実行**:
   - ター미널を開き、以下のコマンドを実行します：
     ```bash
     python spoof_jar.py <path_to_official_bms_ir_jar>
     ```
- 処理が完了すると、元のファイルは `.bak` 拡張子でバックアップされ、改変されたクラスバイトが元のファイル名に上書き保存されます。
