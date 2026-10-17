set_project("arc")
set_languages("cxx20", "c11")
add_rules("plugin.compile_commands.autoupdate", { outputdir = ".vscode" })

option("git_mirror", {
	description = "Github Mirror",
	default = os.getenv("GITHUB_MIRROR") or "",
	showmenu = true,
})

ARCH_MAP = {
	["arm64-v8a"] = "arm64",
}

local out_dir = path.join(os.projectdir(), "lwjgl/natives/%s")
if is_plat("android") then
	out_dir = out_dir .. "-android"
else
	out_dir = out_dir .. "-desktop"
end
out_dir = out_dir .. "/libs/"
if is_plat("linux") then
	out_dir = out_dir .. "linux"
elseif is_plat("android") then
	out_dir = out_dir .. "android"
elseif is_plat("windows") then
	out_dir = out_dir .. "windows"
end
out_dir = out_dir .. "/"
out_dir = out_dir .. (ARCH_MAP[get_config("arch")] or get_config("arch") or "unknown")
out_dir = out_dir .. "/"
ARC_OUR_DIR = out_dir
function GET_OUT(name)
	return string.format(ARC_OUR_DIR, name)
end

local modules = { "lwjgl" }
for _, module in ipairs(modules) do
	includes(path.join(module, "csrc"))
end

task("clean-sources")
do
	set_category("action")
	set_menu({
		usage = "xmake clean-sources",
		description = "Remove cloned source directory",
	})
	on_run(function()
		for _, dir in ipairs(modules) do
			local xdir = path.join(dir, "build/xmake")
			if os.isdir(xdir) then
				print("Removing: " .. xdir)
				os.rmdir(xdir)
			end
		end
	end)
end
