#!/usr/bin/env bash
# 生成发布到 GitHub Release 的两个附件：
#   dist/smartschedule-<版本>.apk   安装包（从已签名的 APK 复制并改名）
#   dist/update.txt                第 1 行版本号，第 2 行 sha256，其余为更新说明
#
# 用法： tools/make_release_assets.sh <已签名 APK> <版本号> <更新说明文件>
# 例：   tools/make_release_assets.sh app/build/outputs/apk/release/app-release.apk 5.2 notes.md
set -euo pipefail

apk="${1:?缺少 APK 路径}"
ver="${2:?缺少版本号，如 5.2}"
notes="${3:?缺少更新说明文件}"

[[ "$ver" =~ ^[0-9]+(\.[0-9]+)*$ ]] || { echo "版本号只能是数字和点：$ver" >&2; exit 1; }
[[ -f "$apk" ]] || { echo "找不到 APK：$apk" >&2; exit 1; }
[[ -f "$notes" ]] || { echo "找不到更新说明：$notes" >&2; exit 1; }

out="dist"
mkdir -p "$out"
cp "$apk" "$out/smartschedule-$ver.apk"
sha="$(sha256sum "$out/smartschedule-$ver.apk" | cut -d' ' -f1)"

{
  echo "$ver"
  echo "sha256: $sha"
  cat "$notes"
} > "$out/update.txt"

echo "已生成 $out/："
ls -l "$out"
echo
echo "下一步（需要 gh 已登录）："
echo "  gh release create v$ver $out/smartschedule-$ver.apk $out/update.txt --title \"智能课表 $ver\" --notes-file \"$notes\""
