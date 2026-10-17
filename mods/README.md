# mods/ — compile-time modpack goes here (NEVER committed, NEVER shipped)
#
# Drop the full Paladium modpack jars here (the 70 jars from
# https://cdn.46anarchy.fr/manifest.json) PLUS unimixins-all.jar:
#
#   https://github.com/LegacyModdingMC/UniMixins/releases/download/0.3.2/%2Bunimixins-all-1.7.10-0.3.2.jar
#   → save as unimixins-all.jar
#
# They are `compileOnly` (like FS-Patches): the launcher provides every mod
# at runtime. This folder exists so `javac` resolves Paladium's closed
# classes when writing shims against them.
#
# CI (release workflow) fetches only the subset the mixins resolve at
# compile time — the Mixin AP reads every @Mixin target:
# optifine-prod, PalaMod-9.5.0, PalaVoiceChat-1.1.0, Zephyr-UI-8.5.0
# (+ unimixins-all).
