// hab-icon: front (north) and side (east) orthographic captures of my read-only tab, saved next to this script.
(function () {
    const fs = require('fs');
    const dir = '/home/emppu/Projects/Minecraft Datapacks/mods/HotAirBalloons/dev/art/candidates/';
    const pv = Preview.selected;
    const out = {};
    const hid = [];
    ['grid', 'gizmos'].forEach(() => {});
    if (Canvas.gizmos) Canvas.gizmos.forEach(g => { if (g.visible) { g.visible = false; hid.push(g); } });
    const grids = (Canvas.scene.children || []).filter(c => c.name === 'grid_group' || c.isGridHelper);
    grids.forEach(g => { if (g.visible) { g.visible = false; hid.push(g); } });
    for (const id of ['north', 'east']) {
        pv.loadAnglePreset(DefaultCameraPresets.find(p => p.id === id));
        pv.camOrtho.zoom = 0.0 + 1;
        pv.camOrtho.position.y = 92; pv.camOrtho.updateProjectionMatrix();
        pv.controls.target.set(0, 92, 0);
        pv.camOrtho.zoom = 0.15;
        pv.camOrtho.updateProjectionMatrix();
        pv.render();
        const url = pv.canvas.toDataURL('image/png');
        fs.writeFileSync(dir + 'bb_front_' + id + '_raw.png', Buffer.from(url.split(',')[1], 'base64'));
        out[id] = {w: pv.canvas.width, h: pv.canvas.height, top: pv.camOrtho.top, bottom: pv.camOrtho.bottom, zoom: pv.camOrtho.zoom, pos: pv.camOrtho.position.toArray()};
    }
    hid.forEach(g => g.visible = true);
    return JSON.stringify(out);
})();
