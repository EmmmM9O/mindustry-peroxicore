local xmake_dir = path.join(os.scriptdir(), "../build/xmake/")
local gen_dir = path.join(os.scriptdir(), "../build/generated/c/")

option("soloud_git", {
	description = "Git repository URL for SoLoud",
	default = "https://github.com/Anuken/soloud.git",
	showmenu = true,
})

option("soloud_tag", {
	description = "Git tag/branch to checkout for SoLoud",
	default = "2026.10.01",
	showmenu = true,
})
target("peroxicore")
do
  set_runtimes("c++_static")
	set_targetdir(path.join(GET_OUT("natives"), "peroxicore"))
	add_files("core/*.cpp")
	add_files(path.join(gen_dir, "core/*.c"))
	add_includedirs("core")
	set_kind("shared")
end

local soloud_dir = path.join(xmake_dir, "soloud")
target("arc")
do
	set_kind("shared")
	on_load(function(_)
		os.mkdir(xmake_dir)
		import("tools", { rootdir = os.projectdir() }).fetch(
			soloud_dir,
			get_config("soloud_git"),
			get_config("soloud_tag")
		)
	end)
	add_files("soloud/soloud_c_arc.cpp")
	local sources = {
		"src/core/*.cpp",
		"src/audiosource/wav/*.cpp",
		"src/audiosource/wav/*.c",
		"src/filter/*.cpp",
	}
	local includesDir = { "include", "src/audiosource/wav" }
	if is_plat("macosx") then
		table.insert(sources, "src/backend/coreaudio/*.mm")
		table.insert(includesDir, "src/backend/coreaudio")
		add_defines("WITH_COREAUDIO")
		add_links("CoreAudio.framework", "AudioToolbox.framework")
	else
		table.insert(sources, "src/backend/miniaudio/*.cpp")
		table.insert(includesDir, "src/backend/miniaudio")
		add_defines("WITH_MINIAUDIO")
		if is_plat("windows") then
			add_links("avrt", "ole32", "winmm")
		elseif not is_plat("android") then
			add_links("dl", "rt", "m")
			add_packages("libdl")
			add_syslinks("pthread")
		end
	end

	for _, source in ipairs(sources) do
		add_files(path.join(soloud_dir, source))
	end

	add_defines("SOLOUD_MAX_VOICE_COUNT=100")

	for _, source in ipairs(includesDir) do
		add_includedirs(path.join(soloud_dir, source))
	end
end

-- Only c api is needed anyway
target("soloud")
do
	set_targetdir(path.join(GET_OUT("natives"), "peroxicore/audio"))
	set_kind("shared")
	add_deps("arc")
	add_files("soloud/soloud_c_arc.cpp")
	add_files(path.join(soloud_dir, "src/c_api/*.cpp"))

	local includesDir = { "include", "src/audiosource/wav" }
	for _, source in ipairs(includesDir) do
		add_includedirs(path.join(soloud_dir, source))
	end
end
