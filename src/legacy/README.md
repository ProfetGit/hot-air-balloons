# Legacy sources (Minecraft 1.21.1)

1.21.1 is a different Minecraft API from 26.x: no entity render states, `CompoundTag` saving instead of
`ValueInput`/`ValueOutput`, `ResourceLocation` instead of `Identifier`, a `Boat`-style entity interpolation, the old
item, recipe and advancement formats. The files that touch those APIs exist a second time here, and only the
`1.21.1-neoforge` build uses them (`build.neoforge-legacy.gradle.kts`):

- `java/` and `resources/`: a file here replaces the shared file with the same path in `src/main`.
- Everything else in `src/main` (physics, wind, basket shape, config, packets, the mixins that still apply) is shared.
- `selftest/` and the mixins `HumanoidMobRendererMixin` and `MinecraftMixin` are not built for 1.21.1. NeoForge's
  `Entity.shouldRiderSit()` replaces the first one.

When a shared file changes in a way that touches one of the files here, change the copy here as well.
