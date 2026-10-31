// hab-icon: opens balloon.bbmodel read-only in my own tab (global HABICON), unsets the save path so it can't be saved over.
(function () {
    const fs = require('fs');
    const path = '/home/emppu/Projects/Minecraft Datapacks/mods/HotAirBalloons/dev/art/balloon.bbmodel';
    window.HABICON = window.HABICON || {};
    const old = ModelProject.all.find(p => p.uuid === window.HABICON.uuid);
    if (old) { old.select(); old.close(true); }
    const model = JSON.parse(fs.readFileSync(path, 'utf8'));
    Codecs.project.load(model, {path: path, name: 'balloon.bbmodel'});
    Project.save_path = '';
    Project.export_path = '';
    Project.name = 'hab_icon_ref (read only)';
    window.HABICON.uuid = Project.uuid;
    return JSON.stringify({uuid: Project.uuid, cubes: Cube.all.length, textures: Texture.all.length, previews: Preview.all.length, presets: Object.keys(window.DefaultCameraPresets || {}).length});
})();
