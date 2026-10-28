#!/bin/sh
# Spec -> Blockbench tab + balloon.bbmodel -> BalloonMesh.java + layout.json -> Aseprite textures -> Blockbench textures.
# Steps 1 and 4 need Blockbench open with the MCP plugin (port 3333); bb.py drives it over HTTP.
set -e
cd "$(dirname "$0")"
python3 make_model.py
python3 bb.py bb_build.js
python3 export.py
/home/emppu/.local/share/Steam/steamapps/common/Aseprite/aseprite --batch --script paint.lua
python3 bb.py bb_build.js
