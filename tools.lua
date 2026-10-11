local mirror = get_config("git_mirror")

function fetch(dir, repo, tag)
  if not os.isdir(dir) then
    print(string.format("Cloning %s (tag: %s)...", mirror .. repo, tag))
    os.mkdir(path.directory(dir))
    local result = os.execv("git", { "clone", "--depth", "1", "--branch", tag, mirror .. repo, dir })
    if not result then
      print("Failed to clone " .. repo .. " repository")
      return false
    end
    print("Done")
    return true
  elseif false then
    print("Updating " .. repo)
    os.execv("git", { "-C", dir, "remote", "set-url", "origin", mirror .. repo })
    os.execv("git", { "-C", dir, "fetch", "--depth", "1", "origin", tag })
    os.execv("git", { "-C", dir, "checkout", "--force", "FETCH_HEAD" })
  end
  return false
end

function downloadTar(dir, tmp, url)
  if os.isdir(dir) then
    return true
  end
  print(string.format("Downloading %s to %s", url, dir))
  local tar = downloadFile(tmp, url)
  if tar:len() == 0 then
    return false
  end
  print(string.format("Unzip %s", tar))
  os.mkdir(tmp)
  local extract_ok = os.execv("tar", { "-xf", tar, "-C", tmp })
  if not extract_ok then
    print("Unzip fail")
    return false
  end
  local files = os.filedirs(path.join(tmp, "*"))
  local extracted_folder = nil
  for _, f in ipairs(files) do
    if os.isdir(f) then
      extracted_folder = f
      break
    end
  end
  if not extracted_folder then
    print("Extracted folder not found")
    return false
  end
  os.mv(extracted_folder, dir)
  os.rm(tar)
  print("Done")
  return true
end

function downloadFile(dir, url)
  os.mkdir(dir)
  local filename = url:match("([^/]+)$")
  if not filename then
    print("can not find name in url")
    return ""
  end
  local filepath = path.join(dir, filename)
  if os.isfile(filepath) then
    return filepath
  end
  os.mkdir(path.directory(filepath))
  print(string.format("Downloading %s to %s", filename, filepath))
  local ok = os.execv("wget", { "-O", filepath, url })
  if not ok then
    print("Download fail")
    os.rm(filepath)
    return ""
  end
  return filepath
end
