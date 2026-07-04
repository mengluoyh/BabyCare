#!/bin/bash
# 一键推送脚本：从Android工作区同步并推送到GitHub
# 使用方法：在Termux或终端中执行 bash git_push.sh

echo "📦 同步文件到 /root/BabyCare/ ..."
cp -rf /storage/emulated/0/APPAndroid/BabyCare/* /root/BabyCare/
cp -rf /storage/emulated/0/APPAndroid/BabyCare/.* /root/BabyCare/ 2>/dev/null

echo "📝 提交更改..."
cd /root/BabyCare
git add -A

# 如果有更改则提交
if ! git diff --cached --quiet; then
    git commit -m "📱 $(date '+%Y-%m-%d %H:%M') 更新"
    echo "🚀 推送到 GitHub ..."
    /root/BabyCare/expect_push.sh
else
    echo "✅ 没有新更改需要提交"
fi