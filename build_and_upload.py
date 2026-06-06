import os
import sys
import glob
import shutil
import subprocess
import json
import urllib.request
import urllib.error
import urllib.parse

def get_github_token():
    # 1. Try Git Credential Manager first, as it contains the user's local credentials
    try:
        proc = subprocess.run(
            ["git", "credential", "fill"],
            input="protocol=https\nhost=github.com\n\n",
            capture_output=True,
            text=True,
            check=True
        )
        for line in proc.stdout.splitlines():
            if line.startswith("password="):
                val = line.split("=", 1)[1].strip()
                if val and "dummy" not in val.lower():
                    return val
    except Exception as e:
        print(f"Note: Git Credential Manager query failed: {e}")

    # 2. Fall back to environment variables (excluding mock/agent tokens)
    for env_var in ["GH_TOKEN", "GITHUB_TOKEN"]:
        token = os.environ.get(env_var)
        if token and "dummy" not in token.lower():
            return token
            
    return None


def make_request(url, method, headers, data=None):
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()

def build_plugin():
    print("[Build] Starting compilation...")
    # Locate JDK
    jdk_dirs = [
        "C:\\Program Files\\Java\\jdk-25.0.2",
        "C:\\Program Files\\Java\\jdk-17",
    ]
    javac_path = None
    jar_path = None
    for jdk in jdk_dirs:
        javac = os.path.join(jdk, "bin", "javac.exe")
        jar = os.path.join(jdk, "bin", "jar.exe")
        if os.path.exists(javac) and os.path.exists(jar):
            javac_path = javac
            jar_path = jar
            break
            
    if not javac_path:
        print("Error: Could not find JDK 25 or JDK 17 in C:\\Program Files\\Java\\")
        sys.exit(1)
        
    print(f"[Build] Using compiler: {javac_path}")
    
    # Locate dependency
    dep_patterns = [
        "..\\lr2oraja-endlessdream-main(lr2oraja 구동기)\\dist\\lr2oraja-*.jar",
        "..\\lr2oraja(lr2oraja 구동기).jar",
        "..\\lr2oraja.jar"
    ]
    dependency_jar = None
    for pattern in dep_patterns:
        matches = glob.glob(pattern)
        if matches:
            matches.sort()
            dependency_jar = matches[-1]
            break
            
    if not dependency_jar:
        print("Error: Could not find dependency lr2oraja*.jar in parent or build directories.")
        sys.exit(1)
        
    print(f"[Build] Using dependency: {dependency_jar}")
    
    # Cleanup old files
    if os.path.exists("bin"):
        shutil.rmtree("bin")
    if os.path.exists("BMS-IR.jar"):
        os.remove("BMS-IR.jar")
        
    os.makedirs("bin", exist_ok=True)
    
    # Compile
    source_file = os.path.join("src", "bms", "player", "beatoraja", "ir", "LR2IRConnectionCustom.java")
    compile_cmd = [
        javac_path,
        "-source", "17",
        "-target", "17",
        "-encoding", "UTF-8",
        "-cp", dependency_jar,
        "-d", "bin",
        source_file
    ]
    print(f"[Build] Running command: {' '.join(compile_cmd)}")
    res = subprocess.run(compile_cmd)
    if res.returncode != 0:
        print("Error: Compilation failed.")
        sys.exit(1)
        
    # Package
    package_cmd = [
        jar_path,
        "cvf", "BMS-IR.jar",
        "-C", "bin",
        "bms"
    ]
    print(f"[Build] Running command: {' '.join(package_cmd)}")
    res = subprocess.run(package_cmd)
    if res.returncode != 0:
        print("Error: Packaging failed.")
        sys.exit(1)
        
    # Cleanup temp
    if os.path.exists("bin"):
        shutil.rmtree("bin")
        
    print("[Build] Success! Generated BMS-IR.jar")

def upload_release(owner, repo, tag, file_path):
    token = get_github_token()
    if not token:
        print("Error: Could not retrieve GitHub token.")
        sys.exit(1)

    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "Release-Uploader"
    }

    # 1. Get release by tag
    url = f"https://api.github.com/repos/{owner}/{repo}/releases/tags/{tag}"
    print(f"[Release] Checking release for tag {tag} in {owner}/{repo}...")
    status, body = make_request(url, "GET", headers)

    if status == 200:
        release = json.loads(body)
        print(f"[Release] Found existing release ID: {release['id']}. Deleting it to update the release date and commit tag...")
        
        # Delete release
        del_url = f"https://api.github.com/repos/{owner}/{repo}/releases/{release['id']}"
        del_status, _ = make_request(del_url, "DELETE", headers)
        if del_status in (200, 204):
            print("[Release] Deleted existing release successfully.")
        else:
            print(f"[Release] Warning: Failed to delete release: {del_status}")

        # Delete remote tag ref
        del_tag_url = f"https://api.github.com/repos/{owner}/{repo}/git/refs/tags/{tag}"
        del_tag_status, _ = make_request(del_tag_url, "DELETE", headers)
        if del_tag_status in (200, 204):
            print(f"[Release] Deleted remote tag ref {tag} successfully.")
        else:
            print(f"[Release] Warning: Failed to delete remote tag ref: {del_tag_status}")

    # Recreate the release (this will automatically create a new tag pointing to the latest commit)
    print(f"[Release] Creating a new release for tag {tag}...")
    create_url = f"https://api.github.com/repos/{owner}/{repo}/releases"
    release_body = (
        "# BMS-IR 커스텀 연동 플러그인 v1.2.9 릴리즈\n\n"
        "공식 BMS-IR 플러그인 `bms_ir_ed_0.0.21.jar` 업데이트에 대응하여 신규 기능 지원 및 사용자 지정 위장 모드 설정 기능이 추가되었습니다.\n\n"
        "### 🚀 업데이트 내역 (v1.2.9)\n\n"
        "1. **식별 위장 모드 사용자 정의 설정 지원 (`bmsir-spoof.txt`)**\n"
        "   - 게임 실행 시 플러그인 폴더(`ir/`) 내에 `bmsir-spoof.txt` 설정 파일이 자동으로 생성됩니다.\n"
        "   - 플레이어는 이 설정 파일을 텍스트 에디터로 열어 전송 시 사용할 클라이언트 식별 방식을 직접 구성할 수 있습니다:\n"
        "     - `lr2`: 순정 LR2 클라이언트로 위장하여 전송합니다. (인터넷 랭킹 연동이 가장 안정적이며, 상세 해시 및 beatoraja 메타데이터를 제외하여 구동기 제약을 우회합니다. User-Agent: `LR2`)\n"
        "     - `ed`: Endless Dream 버전의 lr2oraja 클라이언트로 위장합니다. (User-Agent: `BmsIRUpload/100130`, 클래스 검증 해시 및 variant 메타데이터 전송)\n"
        "     - `vanilla`: 순정 lr2oraja 클라이언트로 위장합니다. (User-Agent: `BmsIRUpload/100130`, 클래스 검증 해시 및 variant 메타데이터 전송)\n\n"
        "2. **신규 게이지 및 클리어 통계 파라미터 전송 (0.0.21 스펙 준수)**\n"
        "   - 플레이 데이터 전송 시, 공식 v0.0.21 버전에 도입된 아래의 세부 정보 파라미터를 추가 전송합니다:\n"
        "     - `clear_type`: beatoraja 클리어 타입의 내부 ID (1~10)\n"
        "     - `gauge_type`: 인게임 게이지 종류의 내부 ID (0~5)\n"
        "     - `gauge_option`: 매핑된 LR2 규격 게이지 상태 번호\n\n"
        "---\n\n"
        "### 🚀 이전 업데이트 내역 (v1.2.8)\n\n"
        "- **순정 구동기 RANDOM 옵션 완벽 연동**: 순정 구동기의 16777215 범위 랜덤 시드를 LR2 규격(0~32766) 난수 배치와 역분석을 통해 자동 매핑하여, 순정 구동기에서도 RANDOM 점수가 완벽히 등록됩니다.\n"
        "- **인게임 라이벌(Rival) 실시간 비교 연동**: 홈페이지 마이페이지 파싱 기능을 통해 인게임 선곡 및 대기실 화면에 라이벌 스펙과 점수가 정상 동기화됩니다.\n"
        "- **MAX Clear (Clear Type 10) 완벽 보존**: All PGREAT 퍼펙트 스코어 달성 시 퍼펙트 대신 beatoraja만의 MAX Clear 규격으로 랭킹에 매핑 전송됩니다.\n"
        "- **이중 전송 방지 및 최적화**: 렉 유발 방지를 위한 로그인 시 9키 난수 캐시 백그라운드 생성(Prewarm) 및 리절트 중복 전송 문제를 해결했습니다.\n"
    )
    payload = json.dumps({
        "tag_name": tag,
        "name": tag,
        "body": release_body,
        "draft": False,
        "prerelease": False
    }).encode('utf-8')
    create_headers = headers.copy()
    create_headers["Content-Type"] = "application/json"
    status, body = make_request(create_url, "POST", create_headers, payload)
    if status in (200, 201):
        release = json.loads(body)
        print(f"[Release] Created release ID: {release['id']}")
    else:
        print(f"[Release] Failed to create release: {status} - {body.decode('utf-8')}")
        sys.exit(1)

    file_name = os.path.basename(file_path)
    
    # 2. Check and delete existing asset with the same name
    assets = release.get("assets", [])
    for asset in assets:
        if asset["name"] == file_name:
            print(f"[Release] Found existing asset {file_name} (ID: {asset['id']}). Deleting it...")
            del_url = f"https://api.github.com/repos/{owner}/{repo}/releases/assets/{asset['id']}"
            del_status, del_body = make_request(del_url, "DELETE", headers)
            if del_status in (200, 204, 240):
                print(f"[Release] Deleted existing asset successfully.")
            else:
                print(f"[Release] Failed to delete existing asset: {del_status} - {del_body.decode('utf-8')}")

    # 3. Upload file
    upload_url_template = release["upload_url"]
    if "{" in upload_url_template:
        upload_url = upload_url_template.split("{")[0]
    else:
        upload_url = upload_url_template

    upload_url = f"{upload_url}?name={urllib.parse.quote(file_name)}"
    print(f"[Release] Uploading {file_name} to {upload_url}...")
    
    with open(file_path, "rb") as f:
        file_data = f.read()

    upload_headers = headers.copy()
    upload_headers["Content-Type"] = "application/octet-stream"
    upload_headers["Content-Length"] = str(len(file_data))

    upload_status, upload_body = make_request(upload_url, "POST", upload_headers, file_data)
    if upload_status in (200, 201):
        print(f"[Release] Upload complete! Asset created successfully.")
    else:
        print(f"[Release] Failed to upload asset: {upload_status} - {upload_body.decode('utf-8')}")
        sys.exit(1)

if __name__ == "__main__":
    build_plugin()
    upload_release("choco-lily", "lr2oraja-lr2ir", "v1.2.9", "BMS-IR.jar")
