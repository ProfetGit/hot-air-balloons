// Hot Air Balloons icon scene (pack-icon-animation skill). Shared Blockbench: own tab by uuid, own global HABI, BB_LOCK while rendering.
//   eval(require('fs').readFileSync('<this file>', 'utf8')); window.HABI = HABI; HABI.open()     // then, in a later call:
//   HABI.build(); HABI.animate(); HABI.camera()                                                  // then:
//   HABI.render(0, HABI.LEN * 60 - 1)                                                            // 1600px frames -> frames/
// The balloon is dev/art/balloon.bbmodel (box envelope) with the mod's textures (main atlas; envelope = sprites/tex_envelope.png, see smooth_envelope.py).
// Loop: envelope flat on the ground -> three burner puffs inflate it and swing it upright -> squash, lift-off, rise, sway -> vent, land, sigh flat.
var HABI = (function () {
  const fs = require('fs');
  const MOD = '/home/emppu/Projects/Minecraft Datapacks/mods/HotAirBalloons/';
  const DIR = MOD + 'dev/icon/', ART = MOD + 'dev/art/', TEX = DIR + 'sprites/';
  const FPS = 60, DT = 1 / FPS, LEN = 3.2;
  const CAM_POS = [0, 60, 104], CAM_TARGET = [0, 16, 0];
  let CAM_PAN = [0, 76, 0], CAM_ZOOM = 0.33;
  const PITCH = -Math.atan2(CAM_POS[1] - CAM_TARGET[1], CAM_POS[2] - CAM_TARGET[2]) * 180 / Math.PI;
  const YAW = 30;
  const MOUTH = 58;                    // sack pivot: bottom of the skirt
  const R = 45;                        // envelope half-width (HotAirBalloon.ENVELOPE_*)
  const LIFT = 36;                     // rise height in model units
  const q = t => Math.round(t * FPS) / FPS;

  let UUID = null;
  function sel() {
    const p = ModelProject.all.find(p => p.uuid === UUID);
    if (!p) throw new Error('HABI tab closed: run HABI.open()');
    if (!p.selected) p.select();
  }
  const dataURL = f => 'data:image/png;base64,' + fs.readFileSync(f).toString('base64');
  const tex = {};
  function open() {
    const old = ModelProject.all.find(p => p.uuid === window.HABI_UUID);
    if (old) { old.select(); old.close(true); }
    newProject(Formats.free);
    UUID = window.HABI_UUID = Project.uuid;
    Codecs.project.parse(JSON.parse(fs.readFileSync(ART + 'balloon.bbmodel', 'utf8')), ART + 'balloon.bbmodel');
    Project.name = 'hab_icon';
    // envelope: the mod's tinted masks composed by smooth_envelope.py (white base, red stripes)
    const files = { main: MOD + 'src/main/resources/assets/hotairballoons/textures/entity/hot_air_balloon.png', envelope: TEX + 'tex_envelope.png' };
    Texture.all.forEach(t => { if (files[t.name]) t.fromDataURL(dataURL(files[t.name])); tex[t.name] = t; });
    for (const f of fs.readdirSync(TEX).filter(f => /^(fx|key)_.*\.png$/.test(f))) {
      tex[f.slice(0, -4)] = new Texture({ name: f.slice(0, -4) }).fromDataURL(dataURL(TEX + f)).add(false);
    }
    return { uuid: UUID, textures: Object.keys(tex) };
  }
  function reattach() {
    if (!UUID && window.HABI_UUID) UUID = window.HABI_UUID;
    sel();
    Texture.all.forEach(t => { tex[t.name] = t; });
  }

  function group(name, origin, parent, rotation) {
    const g = new Group({ name, origin, rotation: rotation || [0, 0, 0] });
    g.addTo(parent); g.init();
    return g;
  }
  const FACES = ['north', 'south', 'east', 'west', 'up', 'down'];
  function cube(name, from, to, parent, faceTex) {
    const c = new Cube({ name, from, to, box_uv: false });
    c.addTo(parent); c.init();
    for (const f of FACES) {
      const spec = faceTex[f];
      if (spec) c.faces[f].extend({ texture: tex[spec[0]].uuid, uv: spec[1] });
      else c.faces[f].extend({ texture: null });
    }
    return c;
  }

  const G = {};
  const PUFFS = [[-1, 0], [1, 0], [-0.4, 1], [0.5, -1]];   // ground puffs round the basket: x side, depth
  function build() {
    reattach();
    Animation.all.slice().forEach(a => a.remove(false));
    const parts = {};
    Group.all.forEach(g => { parts[g.name] = g; });
    ['root', 'yaw', 'sack', 'sackyaw', 'shadow', 'screen'].forEach(n => parts[n] && parts[n].remove(false));
    Group.all.forEach(g => { parts[g.name] = g; });
    G.root = group('root', [0, 0, 0]);
    G.yaw = group('yaw', [0, 0, 0], G.root, [0, YAW, 0]);
    for (const n of ['basket', 'rigging', 'envelope', 'flame', 'pilot']) G[n] = parts[n];
    G.ropes = [0, 1, 2, 3].map(i => parts['rope_' + i]);
    for (const g of [G.basket, G.flame, G.pilot, ...G.ropes]) g.addTo(G.yaw);
    // the sack (skirt + envelope) pivots at the skirt's mouth so it can lie flat beside the basket and swing upright
    G.sack = group('sack', [0, MOUTH, 0], G.root);
    G.sackyaw = group('sackyaw', [0, MOUTH, 0], G.sack, [0, YAW, 0]);
    G.rigging.addTo(G.sackyaw);              // the skirt
    G.envelope.addTo(G.sackyaw);
    G.flame.extend({ origin: [0, 45, 0] });
    // ground shadow: a key-colour disc on the floor, coloured by make_icon.py under the outline
    G.shadow = group('shadow', [0, 0, 0]);
    cube('shadow_disc', [-30, 0.05, -30], [30, 0.05, 30], G.shadow, { up: ['key_shadow', [0, 0, 16, 16]] });
    // camera-facing FX
    G.screen = group('screen', [0, 0, 0], undefined, [PITCH, 0, 0]);
    PUFFS.forEach((p, i) => {
      const x = p[0] * 22, z = 30 + p[1] * 4;
      const g = G['puff_' + i] = group('puff_' + i, [x, 4, z], G.screen);
      cube('puff_plane', [x - 9, -4, z], [x + 9, 14, z], g, { south: ['fx_puff', [0, 0, 16, 16]] });
    });
    Canvas.updateAll();
    return Group.all.length + ' groups, ' + Cube.all.length + ' cubes';
  }

  // keyframe helper: K(anim, group, channel, [[t, [x,y,z], interp], ...])
  function K(anim, g, channel, keys) {
    const ba = anim.getBoneAnimator(g);
    for (const [t, v, interp] of keys) {
      ba.addKeyframe({ channel, time: q(t), interpolation: interp || 'catmullrom', data_points: [{ x: v[0], y: v[1], z: v[2] }] });
    }
  }
  const s3 = (xz, y) => [xz, y, xz];
  const T = {
    puffs: [0.30, 0.58, 0.86], full: 1.26, crouch: 1.38, lift: 1.42, top: 1.86, vent: 2.36, land: 2.68, sigh: 2.78,
  };
  // sack scale: x = thickness (vertical when lying), y = length, z = width. Lying: rotated 90 about z (crown to the left),
  // pivot moved left of the basket and down so the fattest part rests on the ground (y = R * x - MOUTH).
  const lie = (sx, sy, sz, x) => ({ s: [sx, sy, sz], r: [0, 0, 90], p: [x, R * sx - MOUTH, 0] });
  const D = lie(0.14, 0.7, 1.25, -18);
  function animate() {
    reattach();
    Animation.all.slice().forEach(a => a.remove(false));
    const anim = new Animation({ name: 'icon', length: LEN, loop: 'loop', snapping: FPS }).add();
    anim.select();
    const p = T.puffs;
    const P1 = lie(0.62, 0.8, 1.02, -18), P1s = lie(0.56, 0.8, 1.04, -18);
    const SACK = [
      [0, D], [p[0], D], [p[0] + 0.12, P1], [p[0] + 0.20, P1s],
      [p[1] + 0.10, { s: [0.92, 0.88, 0.96], r: [0, 0, 62], p: [-11, -20, 0] }], [p[1] + 0.20, { s: [0.9, 0.92, 0.96], r: [0, 0, 52], p: [-9, -16, 0] }],
      [p[2] + 0.12, { s: [1.06, 1.1, 1.06], r: [0, 0, -9], p: [0, 0, 0] }], [p[2] + 0.22, { s: [0.97, 0.95, 0.97], r: [0, 0, 5], p: [0, 0, 0] }],
      [p[2] + 0.31, { s: [1.02, 1.02, 1.02], r: [0, 0, -2], p: [0, 0, 0] }], [T.full, { s: [1, 1, 1], r: [0, 0, 0], p: [0, 0, 0] }],
      [T.crouch, { s: [1.05, 0.93, 1.05], r: [0, 0, 0], p: [0, 0, 0] }], [T.lift + 0.06, { s: [0.95, 1.08, 0.95], r: [0, 0, 0], p: [0, 0, 0] }],
      [T.lift + 0.2, { s: [1, 1, 1], r: [0, 0, 0], p: [0, 0, 0] }], [T.land, { s: [1, 1, 1], r: [0, 0, 0], p: [0, 0, 0] }],
      [T.land + 0.06, { s: [1.06, 0.9, 1.06], r: [0, 0, 0], p: [0, 0, 0] }], [T.sigh + 0.06, { s: [0.98, 0.94, 0.98], r: [0, 0, -3], p: [0, 0, 0] }],
      [T.sigh + 0.16, { s: [0.82, 0.88, 1.0], r: [0, 0, 32], p: [-6, -9, 0] }], [T.sigh + 0.26, { s: [0.42, 0.86, 1.1], r: [0, 0, 76], p: [-16, R * 0.42 - MOUTH + 3, 0] }],
      [T.sigh + 0.34, lie(0.11, 0.7, 1.28, -19)], [LEN - 0.02, D], [LEN, D],
    ];
    K(anim, G.sack, 'scale', SACK.map(([t, k]) => [t, k.s]));
    K(anim, G.sack, 'rotation', SACK.map(([t, k]) => [t, k.r]));
    K(anim, G.sack, 'position', SACK.map(([t, k]) => [t, k.p]));
    // ropes: slack (flat) while the sack lies, taut once it stands
    const slack = [[0, [1, 0.04, 1], 'linear'], [p[1] + 0.1, [1, 0.04, 1], 'linear'], [p[2] + 0.12, [1, 1, 1], 'linear'],
      [T.sigh + 0.06, [1, 1, 1], 'linear'], [T.sigh + 0.2, [1, 0.04, 1], 'linear'], [LEN, [1, 0.04, 1], 'linear']];
    for (const r of G.ropes) K(anim, r, 'scale', slack);
    // flame: small pilot glow, a burst per puff, a big roar at lift-off, low on the vent
    const fl = [[0, s3(0.35, 0.35), 'linear']];
    for (const t of p) fl.push([t - 0.02, s3(0.35, 0.35), 'linear'], [t + 0.04, s3(1.3, 1.6), 'linear'], [t + 0.16, s3(0.6, 0.6), 'linear']);
    fl.push([T.crouch - 0.04, s3(0.6, 0.6), 'linear'], [T.lift, s3(1.5, 2.1), 'linear'], [T.lift + 0.3, s3(1.3, 1.7), 'linear'],
      [T.top, s3(0.9, 1.0), 'linear'], [T.vent, s3(0.9, 1.0), 'linear'], [T.vent + 0.1, s3(0.35, 0.35), 'linear'], [LEN, s3(0.35, 0.35), 'linear']);
    K(anim, G.flame, 'scale', fl);
    // whole balloon: crouch, lift-off stretch, rise with ease-out, bob, vent down, land squash
    K(anim, G.root, 'scale', [
      [0, s3(1, 1)], [T.full, s3(1, 1)], [T.crouch, s3(1.06, 0.92)], [T.lift, s3(0.95, 1.08)], [T.lift + 0.2, s3(1, 1)],
      [T.land - 0.02, s3(0.98, 1.03)], [T.land + 0.04, s3(1.08, 0.88)], [T.land + 0.12, s3(0.97, 1.03)], [T.land + 0.2, s3(1, 1)], [LEN, s3(1, 1)],
    ]);
    const L = LIFT;
    K(anim, G.root, 'position', [
      [0, [0, 0, 0]], [T.lift, [0, 0, 0]], [T.lift + 0.12, [0, L * 0.36, 0]], [T.lift + 0.26, [0, L * 0.68, 0]], [T.lift + 0.42, [0, L * 0.9, 0]],
      [T.top, [0, L, 0]], [T.vent, [0, L, 0]],
      [T.vent + 0.12, [0, L * 0.9, 0]], [T.vent + 0.24, [0, L * 0.6, 0]], [T.land - 0.04, [0, L * 0.12, 0], 'linear'], [T.land, [0, 0, 0], 'linear'], [LEN, [0, 0, 0]],
    ]);
    K(anim, G.root, 'rotation', [
      [0, [0, 0, 0]], [T.lift, [0, 0, 0]], [T.lift + 0.18, [0, 0, -4]], [T.lift + 0.34, [0, 0, 2]], [T.top, [0, 0, 0]],
      [T.vent, [0, 0, 0]], [T.land, [0, 0, 0]], [LEN, [0, 0, 0]],
    ]);
    const sh = y => s3(1 - 0.3 * y / L, 1);
    K(anim, G.shadow, 'scale', [
      [0, sh(0)], [T.lift, sh(0)], [T.lift + 0.12, sh(L * 0.36)], [T.lift + 0.26, sh(L * 0.68)], [T.lift + 0.42, sh(L * 0.9)], [T.top, sh(L)],
      [T.vent, sh(L)], [T.vent + 0.12, sh(L * 0.9)], [T.vent + 0.24, sh(L * 0.6)], [T.land - 0.04, sh(L * 0.12), 'linear'], [T.land, sh(0), 'linear'], [LEN, sh(0)],
    ]);
    // dust puffs at lift-off and landing: pop in, drift out, shrink away
    PUFFS.forEach((pf, i) => {
      const g = G['puff_' + i], keys = [[0, s3(0, 0), 'linear']], pos = [[0, [0, 0, 0], 'linear']];
      for (const [t0, k] of [[T.lift + 0.01 * i, 1], [T.land + 0.01 * i, 0.8]]) {
        keys.push([t0 - DT, s3(0, 0), 'linear'], [t0 + 0.05, s3(1.1 * k, 1.1 * k), 'linear'], [t0 + 0.2, s3(0.8 * k, 0.8 * k), 'linear'], [t0 + 0.32, s3(0, 0), 'linear']);
        pos.push([t0 - DT, [0, 0, 0], 'linear'], [t0 + 0.32, [pf[0] * 12, 5, 0], 'linear'], [t0 + 0.32 + DT, [0, 0, 0], 'linear']);
      }
      keys.push([LEN, s3(0, 0), 'linear']);
      K(anim, g, 'scale', keys);
      K(anim, g, 'position', pos);
    });
    Animator.preview();
    return 'animated';
  }

  function camera(zoom, pan) {
    reattach();
    const p = Preview.selected;
    p.setProjectionMode(true);
    const pn = pan || CAM_PAN;
    const pos = CAM_POS.map((v, i) => v + pn[i]), tgt = CAM_TARGET.map((v, i) => v + pn[i]);
    p.camera.position.set(...pos);
    p.controls.target.set(...tgt);
    p.camera.lookAt(...tgt);
    p.camera.zoom = zoom || CAM_ZOOM; p.camera.updateProjectionMatrix();
    p.controls.update();
  }
  function setTime(t) {
    Timeline.setTime(t);
    Animator.preview();
  }
  function render(first, last, res, dir, step) {
    reattach();
    res = res || 1600;
    dir = dir || DIR + 'frames/';
    if (window.BB_LOCK && window.BB_LOCK.until > Date.now() && window.BB_LOCK.owner !== 'habi') return { busy: window.BB_LOCK };
    window.BB_LOCK = { owner: 'habi', until: Date.now() + 15 * 60 * 1000 };
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    const shot = f => new Promise(done => {
      setTime(f * DT);
      Screencam.advancedScreenshot(Preview.selected, { angle_preset: 'view', resolution: [res, res], anti_aliasing: 'none', shading: false }, url => {
        fs.writeFileSync(dir + 'frame_' + String(f).padStart(3, '0') + '.png', Buffer.from(url.split(',')[1], 'base64'));
        done();
      });
    });
    return (async () => {
      try { for (let f = first; f <= last; f += (step || 1)) await shot(f); } finally { window.BB_LOCK = null; }
      return `rendered ${first}..${last}`;
    })();
  }
  function scaleSweep() {
    reattach();
    const bad = [];
    for (let f = 0; f <= Math.round(LEN * FPS); f++) {
      setTime(f * DT);
      Group.all.forEach(g => { const s = g.mesh.scale; if (s.x < 0 || s.y < 0 || s.z < 0) bad.push(g.name + '@' + f); });
    }
    return bad.length ? bad.join(',') : 'no negative scale';
  }
  function save() {
    reattach();
    fs.writeFileSync(DIR + 'hab_icon_anim.bbmodel', Codecs.project.compile());
    return 'saved';
  }

  return { FPS, DT, LEN, T, G, tex, open, build, animate, camera, render, setTime, scaleSweep, save, sel: reattach,
    pan: v => { CAM_PAN = v; }, zoom: v => { CAM_ZOOM = v; } };
})();
