#include "peroxicore.h"
#include "common_tools.h"
#include <fstream>
#include <optional>
#include <regex>
#include <string>

std::optional<std::string> find_lib_path(const std::string &pattern) {
#if defined(__linux__)
  std::ifstream maps("/proc/self/maps");
  if (!maps)
    return std::nullopt;

  std::regex re(pattern, std::regex::extended);
  std::string line;
  while (std::getline(maps, line)) {
    if (!std::regex_search(line, re))
      continue;

    auto pos = line.find('/');
    if (pos == std::string::npos)
      continue;
    return line.substr(pos);
  }
  return std::nullopt;
#elif
  return std::nullopt;
#endif
}

EXTERN_C_ENTER

JNIEXPORT jstring JNICALL Java_peroxicore_lwjgl_LibraryUtil_findLibpath(
    JNIEnv *env, jclass clazz, jstring pattern) {
  const char *pat = env->GetStringUTFChars(pattern, nullptr);
  if (!pat)
    return nullptr;
  auto result = find_lib_path(pat);
  env->ReleaseStringUTFChars(pattern, pat);
  if (!result.has_value())
    return nullptr;
  return env->NewStringUTF(result.value().c_str());
}

EXTERN_C_EXIT
