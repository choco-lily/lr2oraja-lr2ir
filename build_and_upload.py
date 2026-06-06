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
    payload = json.dumps({
        "tag_name": tag,
        "name": tag,
        "body": f"Release {tag}",
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
    upload_release("choco-lily", "lr2oraja-lr2ir", "v1.2.8", "BMS-IR.jar")
