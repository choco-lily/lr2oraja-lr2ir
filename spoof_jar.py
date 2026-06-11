import os
import sys
import zipfile
import shutil
import struct
import re
import hashlib

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

def parse_and_patch_class(data, client_hash, plugin_hash):
    offset = 0
    magic = struct.unpack_from('>I', data, offset)[0]
    offset += 4
    if magic != 0xCAFEBABE:
        raise ValueError("Invalid magic")
        
    minor = struct.unpack_from('>H', data, offset)[0]
    offset += 2
    major = struct.unpack_from('>H', data, offset)[0]
    offset += 2
    
    cp_count = struct.unpack_from('>H', data, offset)[0]
    offset += 2
    
    cp = [None]
    i = 1
    while i < cp_count:
        tag = data[offset]
        entry_start = offset
        offset += 1
        if tag == 1: # Utf8
            length = struct.unpack_from('>H', data, offset)[0]
            offset += 2
            val = data[offset:offset+length]
            offset += length
            cp.append({'tag': tag, 'val': val})
        elif tag in (3, 4): # Integer, Float
            val_bytes = data[offset:offset+4]
            offset += 4
            cp.append({'tag': tag, 'bytes': val_bytes})
        elif tag in (5, 6): # Long, Double
            val_bytes = data[offset:offset+8]
            offset += 8
            cp.append({'tag': tag, 'bytes': val_bytes})
            cp.append(None)
            i += 1
        elif tag in (7, 8, 16, 19, 20): # Class, String, MethodType, Module, Package
            val = struct.unpack_from('>H', data, offset)[0]
            offset += 2
            cp.append({'tag': tag, 'val': val})
        elif tag in (9, 10, 11, 12, 17, 18): # Fieldref, Methodref, InterfaceMethodref, NameAndType, Dynamic, InvokeDynamic
            val_bytes = data[offset:offset+4]
            offset += 4
            cp.append({'tag': tag, 'bytes': val_bytes})
        elif tag == 15: # MethodHandle
            val_bytes = data[offset:offset+3]
            offset += 3
            cp.append({'tag': tag, 'bytes': val_bytes})
        else:
            raise ValueError(f"Unknown tag: {tag}")
        i += 1
        
    # Resolve constant pool indices dynamically
    idx_main_controller = None
    idx_bms_ir_connection = None
    idx_lr2oraja_ed = None
    idx_ed = None
    
    # 1. Resolve Utf8 indices
    for idx in range(1, cp_count):
        entry = cp[idx]
        if entry and entry['tag'] == 1:
            val = entry['val']
            if val == b"bms.player.beatoraja.MainController":
                idx_main_controller = idx
            elif val == b"bms.player.beatoraja.ir.BmsIRConnection":
                idx_bms_ir_connection = idx
            elif val == b"lr2oraja-ed":
                idx_lr2oraja_ed = idx
            elif val == b"ed":
                idx_ed = idx
                
    if not idx_main_controller or not idx_bms_ir_connection or not idx_lr2oraja_ed or not idx_ed:
        print("[Warning] Could not resolve required constant pool strings. Falling back to default string replacement.")
        return modify_class_bytes(data)
        
    # 2. Resolve String (tag 8) indices pointing to those Utf8 entries
    idx_lr2oraja_ed_str = None
    idx_ed_str = None
    for idx in range(1, cp_count):
        entry = cp[idx]
        if entry and entry['tag'] == 8:
            if entry['val'] == idx_lr2oraja_ed:
                idx_lr2oraja_ed_str = idx
            elif entry['val'] == idx_ed:
                idx_ed_str = idx
                
    if not idx_lr2oraja_ed_str or not idx_ed_str:
        print("[Warning] Could not resolve String constants for lr2oraja-ed or ed. Falling back to default string replacement.")
        return modify_class_bytes(data)
        
    print(f"[Info] Found constant indices: MainController_Utf8={idx_main_controller}, BmsIRConnection_Utf8={idx_bms_ir_connection}, lr2oraja-ed_String={idx_lr2oraja_ed_str}, ed_String={idx_ed_str}")
    
    # Patch class name Utf8 contents to hardcoded MD5 hashes
    cp[idx_main_controller]['val'] = client_hash.encode('utf-8')
    cp[idx_bms_ir_connection]['val'] = plugin_hash.encode('utf-8')
    
    access_flags = struct.unpack_from('>H', data, offset)[0]
    this_class = struct.unpack_from('>H', data, offset + 2)[0]
    super_class = struct.unpack_from('>H', data, offset + 4)[0]
    offset += 6
    
    interfaces_count = struct.unpack_from('>H', data, offset)[0]
    offset += 2
    interfaces_bytes = data[offset:offset + interfaces_count * 2]
    offset += interfaces_count * 2
    
    # Fields
    fields_count = struct.unpack_from('>H', data, offset)[0]
    offset += 2
    fields_start = offset
    for _ in range(fields_count):
        offset += 6
        attr_count = struct.unpack_from('>H', data, offset)[0]
        offset += 2
        for _ in range(attr_count):
            offset += 2
            attr_len = struct.unpack_from('>I', data, offset)[0]
            offset += 4 + attr_len
    fields_bytes = data[fields_start:offset]
    
    # Methods
    methods_count = struct.unpack_from('>H', data, offset)[0]
    offset += 2
    
    patched_methods_bytes = bytearray()
    for _ in range(methods_count):
        m_access = struct.unpack_from('>H', data, offset)[0]
        m_name_idx = struct.unpack_from('>H', data, offset + 2)[0]
        m_desc_idx = struct.unpack_from('>H', data, offset + 4)[0]
        m_attr_count = struct.unpack_from('>H', data, offset + 6)[0]
        
        m_name = cp[m_name_idx]['val'].decode('utf-8', errors='ignore')
        m_desc = cp[m_desc_idx]['val'].decode('utf-8', errors='ignore')
        
        patched_methods_bytes.extend(data[offset:offset+8])
        offset += 8
        
        for _ in range(m_attr_count):
            attr_name_idx = struct.unpack_from('>H', data, offset)[0]
            attr_len = struct.unpack_from('>I', data, offset + 2)[0]
            attr_name = cp[attr_name_idx]['val'].decode('utf-8', errors='ignore')
            
            attr_data = bytearray(data[offset : offset + 6 + attr_len])
            
            if attr_name == 'Code':
                code_len = struct.unpack_from('>I', attr_data, 10)[0]
                
                # Patch bytecodes
                if m_name == 'clientKind' and m_desc == '()Ljava/lang/String;':
                    print("  Patching clientKind() to return 'lr2oraja-ed'")
                    if idx_lr2oraja_ed_str <= 255:
                        new_code = bytearray([0x12, idx_lr2oraja_ed_str, 0xb0]) + bytearray([0x00] * (code_len - 3))
                    else:
                        new_code = bytearray([0x13, (idx_lr2oraja_ed_str >> 8) & 0xff, idx_lr2oraja_ed_str & 0xff, 0xb0]) + bytearray([0x00] * (code_len - 4))
                    attr_data[14 : 14 + code_len] = new_code
                    
                elif m_name == 'clientVariant' and m_desc == '()Ljava/lang/String;':
                    print("  Patching clientVariant() to return 'ed'")
                    if idx_ed_str <= 255:
                        new_code = bytearray([0x12, idx_ed_str, 0xb0]) + bytearray([0x00] * (code_len - 3))
                    else:
                        new_code = bytearray([0x13, (idx_ed_str >> 8) & 0xff, idx_ed_str & 0xff, 0xb0]) + bytearray([0x00] * (code_len - 4))
                    attr_data[14 : 14 + code_len] = new_code
                    
                elif m_name == 'clientHash' and m_desc == '()Ljava/lang/String;':
                    print(f"  Patching clientHash() -> MD5: {client_hash}")
                    code_start = 14
                    code_bytes = attr_data[code_start : code_start + code_len]
                    b8_idx = code_bytes.find(0xb8)
                    if b8_idx != -1:
                        attr_data[code_start + b8_idx : code_start + b8_idx + 3] = b'\x00\x00\x00'
                        
                elif m_name == 'pluginHash' and m_desc == '()Ljava/lang/String;':
                    print(f"  Patching pluginHash() -> MD5: {plugin_hash}")
                    code_start = 14
                    code_bytes = attr_data[code_start : code_start + code_len]
                    b8_idx = code_bytes.find(0xb8)
                    if b8_idx != -1:
                        attr_data[code_start + b8_idx : code_start + b8_idx + 3] = b'\x00\x00\x00'
                        
            patched_methods_bytes.extend(attr_data)
            offset += 6 + attr_len
            
    class_attrs_bytes = data[offset:]
    
    # Serialize constant pool back
    cp_bytes = bytearray()
    i = 1
    while i < cp_count:
        entry = cp[i]
        if entry is None:
            i += 1
            continue
        tag = entry['tag']
        cp_bytes.append(tag)
        if tag == 1:
            val = entry['val']
            cp_bytes.extend(struct.pack('>H', len(val)))
            cp_bytes.extend(val)
        elif tag in (3, 4):
            cp_bytes.extend(entry['bytes'])
        elif tag in (5, 6):
            cp_bytes.extend(entry['bytes'])
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            cp_bytes.extend(struct.pack('>H', entry['val']))
        elif tag in (9, 10, 11, 12, 17, 18):
            cp_bytes.extend(entry['bytes'])
        elif tag == 15:
            cp_bytes.extend(entry['bytes'])
        i += 1
        
    new_class_data = bytearray()
    new_class_data.extend(struct.pack('>I', magic))
    new_class_data.extend(struct.pack('>H', minor))
    new_class_data.extend(struct.pack('>H', major))
    new_class_data.extend(struct.pack('>H', cp_count))
    new_class_data.extend(cp_bytes)
    new_class_data.extend(struct.pack('>H', access_flags))
    new_class_data.extend(struct.pack('>H', this_class))
    new_class_data.extend(struct.pack('>H', super_class))
    new_class_data.extend(struct.pack('>H', interfaces_count))
    new_class_data.extend(interfaces_bytes)
    new_class_data.extend(struct.pack('>H', fields_count))
    new_class_data.extend(fields_bytes)
    new_class_data.extend(struct.pack('>H', methods_count))
    new_class_data.extend(patched_methods_bytes)
    new_class_data.extend(class_attrs_bytes)
    
    return bytes(new_class_data)

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
    
    # Try to find a local lr2oraja.jar to get its hash
    parent_dir = os.path.dirname(os.path.abspath(jar_path))
    lr2oraja_path = os.path.join(os.path.dirname(parent_dir), "lr2oraja.jar")
    
    target_client_hash = None
    if os.path.exists(lr2oraja_path):
        try:
            with open(lr2oraja_path, "rb") as f:
                target_client_hash = hashlib.md5(f.read()).hexdigest()
            print(f"[Info] Detected local lr2oraja.jar MD5: {target_client_hash}")
        except Exception as e:
            print(f"[Warning] Failed to calculate local lr2oraja.jar MD5: {e}")
            
    if not target_client_hash:
        target_client_hash = "d5e65ef4bb7c954197a682ee90714f8c"
        print(f"[Info] Using default official lr2oraja.jar MD5: {target_client_hash}")
        
    try:
        with open(backup_path, "rb") as f:
            target_plugin_hash = hashlib.md5(f.read()).hexdigest()
        print(f"[Info] Original plugin MD5: {target_plugin_hash}")
    except Exception as e:
        target_plugin_hash = "58ac609fbf49aef46d8350559da0f8bc"
        print(f"[Warning] Using default plugin MD5: {target_plugin_hash} (Error: {e})")
        
    temp_dir = jar_path + "_temp"
    if os.path.exists(temp_dir):
        shutil.rmtree(temp_dir)
    os.makedirs(temp_dir)
    
    try:
        # Extract the JAR
        with zipfile.ZipFile(backup_path, 'r') as zin:
            zin.extractall(temp_dir)
            
        modified_count = 0
        has_bms_ir_conn = False
        
        # Check if it is the official BMS-IR plugin (contains BmsIRConnection.class)
        conn_path = os.path.join(temp_dir, 'bms', 'player', 'beatoraja', 'ir', 'BmsIRConnection.class')
        if os.path.exists(conn_path):
            has_bms_ir_conn = True
            print("[Info] Official BMS-IR plugin BmsIRConnection.class found. Patching with class rebuilder...")
            with open(conn_path, 'rb') as f:
                data = f.read()
            patched_data = parse_and_patch_class(data, target_client_hash, target_plugin_hash)
            if patched_data != data:
                with open(conn_path, 'wb') as f:
                    f.write(patched_data)
                modified_count += 1
                print("[Success] Patched BmsIRConnection.class successfully!")
                
        if not has_bms_ir_conn:
            print("[Info] Running legacy string-literal client spoofing on all class files...")
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
            print("[Info] No client identifiers or classes found to modify.")
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
 
