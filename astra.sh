#!/bin/sh
# Astra on macOS and Linux: what the .bat files do on Windows. In Terminal, in this folder:
#
#   sh astra.sh             a menu
#   sh astra.sh start       build Astra and open the app        (Start Astra.bat)
#   sh astra.sh installer   build the launcher for players      (Build Installer.bat)
#   sh astra.sh build-new   build the versions being ported     (Build New Versions.bat)
#   sh astra.sh prepare     set up new Minecraft versions       (Prepare New Versions.bat)
#   sh astra.sh server      run only the Astra server           (Start Server Only.bat)
#   sh astra.sh host        put the server online               (Host Astra Online.bat)
#   sh astra.sh duckdns     free permanent address              (Set Up DuckDNS.bat)
#
# "sh astra.sh" works even when this file lost its executable flag (projects copied from Windows
# or GitHub Desktop do), which is also why it starts Gradle with "sh gradlew".
cd "$(dirname "$0")" || exit 1

if ! command -v node >/dev/null 2>&1; then
  echo "Astra needs Node.js 20 or newer."
  if [ "$(uname)" = "Darwin" ]; then
    echo "Get it from https://nodejs.org (or: brew install node), then run this again."
  else
    echo "Get it from https://nodejs.org or your package manager, then run this again."
  fi
  exit 1
fi

cmd="$1"
if [ -z "$cmd" ]; then
  echo "Astra"
  echo "  1) Start Astra (build and open the app)"
  echo "  2) Build the launcher for players"
  echo "  3) Build the new Minecraft versions"
  echo "  4) Prepare new Minecraft versions"
  echo "  5) Start the server only"
  echo "  6) Host Astra online"
  printf "Choose 1-6: "
  read -r choice
  case "$choice" in
    1) cmd=start ;;
    2) cmd=installer ;;
    3) cmd=build-new ;;
    4) cmd=prepare ;;
    5) cmd=server ;;
    6) cmd=host ;;
    *) echo "Nothing chosen."; exit 1 ;;
  esac
fi

exec node tools/astra.js "$cmd"
