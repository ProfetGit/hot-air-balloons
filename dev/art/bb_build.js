// Builds balloon.bbmodel in its own Blockbench tab from model_spec.json (make_model.py). Shared Blockbench: own global
// HAB, own tab by uuid. Textures are loaded when out/main.png and out/envelope_preview.png exist (paint.lua).
(function () {
    const fs = require('fs');
    const dir = '/home/emppu/Projects/Minecraft Datapacks/mods/HotAirBalloons-box/dev/art/';
    const spec = JSON.parse(fs.readFileSync(dir + 'model_spec.json', 'utf8'));
    window.HABBOX = window.HABBOX || {};
    const old = ModelProject.all.find(p => p.uuid === window.HABBOX.uuid);
    if (old) { old.select(); old.close(true); }
    newProject(Formats.free);
    Project.name = 'hot_air_balloon';
    Project.box_uv = true;
    window.HABBOX.uuid = Project.uuid;
    const tex = {};
    for (const [atlas, file] of [['main', 'out/main.png'], ['envelope', 'out/envelope_preview.png']]) {
        const [w, h] = spec.atlases[atlas];
        const t = new Texture({name: atlas, uv_width: w, uv_height: h});
        if (fs.existsSync(dir + file)) t.fromPath(dir + file); else t.fromDataURL(blankPNG(w, h));
        t.name = atlas;
        t.add(false);
        tex[atlas] = t;
    }
    for (const g of spec.groups) {
        const group = new Group({name: g.name, origin: g.pivot}).init();
        for (const c of g.cubes) {
            const cube = new Cube({name: c.name, from: c.from, to: c.to, origin: g.pivot, box_uv: true, uv_offset: c.uv}).addTo(group).init();
            cube.applyTexture(tex[g.atlas], true);
        }
    }
    Canvas.updateAll();
    function blankPNG(w, h) {
        const cv = document.createElement('canvas');
        cv.width = w; cv.height = h;
        return cv.toDataURL();
    }
    Blockbench.writeFile(dir + 'balloon.bbmodel', {content: Codecs.project.compile()});
    return {uuid: Project.uuid, cubes: Cube.all.length, groups: Group.all.length};
})();
