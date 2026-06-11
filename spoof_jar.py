import os
import sys
import zipfile
import shutil
import struct
import re

def modify_class_bytes(data):
    if not data.startswith(b'\xca\xfe\xba\xbe'):
        return data

    offset = 8
    constant_pool_count = struct.unpack_from('>H', data, offset)[0]
    offset += 2
    
    cp_entries = [None] # 1-based indexing
    i = 1
    cp_offset = offset
    
    try:
        while i < constant_pool_count:
            tag = data[cp_offset]
            entry_start = cp_offset
            cp_offset += 1
            
            if tag == 1: # Utf8
                length = struct.unpack_from('>H', data, cp_offset)[0]
                cp_offset += 2
                val_bytes = data[cp_offset:cp_offset+length]
                cp_offset += length
                cp_entries.append({'tag': tag, 'val': val_bytes, 'start': entry_start, 'size': cp_offset - entry_start})
            elif tag in (3, 4): # Integer, Float
                cp_offset += 4
                cp_entries.append({'tag': tag, 'start': entry_start, 'size': cp_offset - entry_start})
            elif tag in (5, 6): # Long, Double
                cp_offset += 8
                cp_entries.append({'tag': tag, 'start': entry_start, 'size': cp_offset - entry_start})
                cp_entries.append(None) # takes two slots
                i += 1
            elif tag in (7, 8, 16, 19, 20): # Class, String, MethodType, Module, Package
                val = struct.unpack_from('>H', data, cp_offset)[0]
                cp_offset += 2
                cp_entries.append({'tag': tag, 'val': val, 'start': entry_start, 'size': cp_offset - entry_start})
            elif tag in (9, 10, 11, 12, 17, 18): # Fieldref, Methodref, InterfaceMethodref, NameAndType, Dynamic, InvokeDynamic
                cp_offset += 4
                cp_entries.append({'tag': tag, 'start': entry_start, 'size': cp_offset - entry_start})
            elif tag == 15: # MethodHandle
                cp_offset += 3
                cp_entries.append({'tag': tag, 'start': entry_start, 'size': cp_offset - entry_start})
            else:
                # Unknown tag or invalid structure, return original bytes safely
                return data
            i += 1
    except Exception:
        # Fallback to original data if parsing fails
        return data
        
    cp_end_offset = cp_offset
    
    # Identify UTF-8 constants referenced by CONSTANT_String (tag 8) entries
    string_indices = set()
    for entry in cp_entries:
        if entry and entry['tag'] == 8:
            string_indices.add(entry['val'])
            
    # Pattern to match: "lr2oraja" optionally followed by space/hyphen/underscore and "ed" or "ir"
    pattern = re.compile(b'^(?:lr2oraja)(?:[\\s_-]?(?:ed|ir))?$', re.IGNORECASE)
    
    new_cp = bytearray()
    idx = 1
    replaced = False
    
    while idx < len(cp_entries):
        entry = cp_entries[idx]
        if entry is None:
            idx += 1
            continue
            
        tag = entry['tag']
        entry_start = entry['start']
        size = entry['size']
        
        if tag == 1:
            val_bytes = entry['val']
            # Only replace string literals (referenced by CONSTANT_String_info)
            if idx in string_indices and pattern.match(val_bytes):
                print(f"  Replacing UTF-8 string literal: '{val_bytes.decode('utf-8', errors='ignore')}' -> 'LR2'")
                val_bytes = b"LR2"
                replaced = True
            
            new_cp.append(1) # tag
            new_cp.extend(struct.pack('>H', len(val_bytes)))
            new_cp.extend(val_bytes)
        else:
            new_cp.extend(data[entry_start:entry_start+size])
            
        idx += 1
        
    if not replaced:
        return data
        
    new_data = bytearray()
    new_data.extend(data[:8]) # magic, version
    new_data.extend(struct.pack('>H', constant_pool_count))
    new_data.extend(new_cp)
    new_data.extend(data[cp_end_offset:])
    return bytes(new_data)

def process_jar(jar_path):
    if not os.path.exists(jar_path):
        print(f"[Error] File not found: {jar_path}")
        return False
        
    if not jar_path.lower().endswith(".jar"):
        print(f"[Error] Not a JAR file: {jar_path}")
        return False
        
    print(f"[Info] Target JAR: {jar_path}")
    backup_path = jar_path + ".bak"
    
    # Create backup copy
    if os.path.exists(backup_path):
        os.remove(backup_path)
    shutil.copyfile(jar_path, backup_path)
    print(f"[Info] Created backup: {backup_path}")
    
    temp_dir = jar_path + "_temp"
    if os.path.exists(temp_dir):
        shutil.rmtree(temp_dir)
    os.makedirs(temp_dir)
    
    try:
        # Extract the JAR
        with zipfile.ZipFile(backup_path, 'r') as zin:
            zin.extractall(temp_dir)
            
        modified_count = 0
        for root, dirs, files in os.walk(temp_dir):
            for file in files:
                if file.lower().endswith(".class"):
                    class_path = os.path.join(root, file)
                    with open(class_path, 'rb') as f:
                        data = f.read()
                    
                    modified_data = modify_class_bytes(data)
                    
                    if modified_data != data:
                        with open(class_path, 'wb') as f:
                            f.write(modified_data)
                        modified_count += 1
                        print(f"[Success] Modified: {os.path.relpath(class_path, temp_dir)}")
                        
        if modified_count == 0:
            print("[Info] No client string identifiers ('LR2oraja', 'LR2oraja ED', 'lr2oraja-ir') found to modify.")
        else:
            print(f"[Info] Modified total of {modified_count} class file(s).")
            
        # Re-package the JAR
        if os.path.exists(jar_path):
            os.remove(jar_path)
            
        added_dirs = set()
        def add_dir_to_zip(zout, dir_path):
            dir_path = dir_path.replace('\\', '/')
            if not dir_path or dir_path in added_dirs:
                return
            parent = os.path.dirname(dir_path)
            if parent:
                add_dir_to_zip(zout, parent)
            zout.writestr(dir_path + '/', b'')
            added_dirs.add(dir_path)

        with zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as zout:
            for root, dirs, files in os.walk(temp_dir):
                for file in files:
                    full_path = os.path.join(root, file)
                    rel_path = os.path.relpath(full_path, temp_dir)
                    
                    parent_dir = os.path.dirname(rel_path)
                    if parent_dir:
                        add_dir_to_zip(zout, parent_dir)
                        
                    zout.write(full_path, rel_path.replace('\\', '/'))
                     
        print(f"[Success] JAR file updated successfully!")
        return True
        
    except Exception as e:
        print(f"[Error] Failed to process JAR: {e}")
        # Restore from backup
        if os.path.exists(backup_path):
            shutil.copyfile(backup_path, jar_path)
            print("[Info] Restored original JAR from backup.")
        return False
        
    finally:
        if os.path.exists(temp_dir):
            shutil.rmtree(temp_dir)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("BMS IR Plugin Client Spoofing CLI Tool")
        print("Usage:")
        print("  python spoof_jar.py <path_to_jar_file>")
        print("  Or drag and drop a .jar file onto spoof_jar.bat")
        print()
        if sys.stdin.isatty():
            input("Press Enter to exit...")
        sys.exit(1)
        
    jar_path = sys.argv[1]
    process_jar(jar_path)
    print()
    if sys.stdin.isatty():
        input("Press Enter to exit...")
