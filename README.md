<h1 align="center">AutoAmethyst</h1>

<h3 align="center">Automatically harvest amethyst shards without breaking Budding Amethyst</h3>

<p align="center">
  <img src="https://img.shields.io/github/downloads/GarlicRot/AutoAmethyst/total?label=Downloads" alt="GitHub Downloads (all assets, all releases)">
  <img src="https://img.shields.io/badge/Minecraft-1.21.4-62b47a?style=flat&logo=minecraft&logoColor=white" alt="Minecraft Version">
  <img src="https://img.shields.io/badge/%F0%9F%A7%84-Approved%20%E2%9C%94%EF%B8%8F-blue?style=flat" alt="🧄 Approved ✔️">
</p>

## Overview

**AutoAmethyst** automatically breaks amethyst buds and clusters attached to  
**Budding Amethyst**, allowing you to farm shards efficiently **without ever
destroying the budding block itself**.

The module uses vanilla-safe packet breaking and internal cooldown handling to
avoid ghost mining, swing loops, and desync.

## Features

- Automatically breaks **amethyst buds & clusters**
- **Never** breaks Budding Amethyst
- Vanilla reach–safe breaking logic
- Intelligent retry cooldown & failure backoff
- Packet-based mining (no ghost blocks)
- Optional swing animation
- Visual rendering for budding blocks and shard targets
- Configurable break modes

## Settings

| Setting | Description |
|-------|-------------|
| **Break** | Select which growth stages to break (Small, Medium, Large, Cluster, or combinations) |
| **Render** | Render Budding Amethyst blocks and shard targets |
| **Budding Color** | Color used to render Budding Amethyst blocks |
| **Target Color** | Color used to render shard targets |
| **Line Width** | Width of rendered block outlines |
| **Retry Cooldown** | Delay (in ticks) before retrying the same shard target |
| **Swing** | Swing the hand when breaking (visual only) |
| **Bind** | Keybind to toggle the module |

<p align="center">
  <img
    src="https://raw.githubusercontent.com/RusherDevelopment/rusherhack-plugins/main/Assets/AutoAmethyst/Module.png"
    alt="AutoAmethyst Module Settings"
    width="250"
  >
</p>

## Break Modes

The **Break** setting allows control over which amethyst growth stages are harvested:

- **Cluster Only**
- **Large Only**
- **Medium Only**
- **Small Only**
- **Small → Large**
- **Large + Cluster**
- **All**

## Installation

1. Download the latest `.jar` from the  
   **[Releases](https://github.com/GarlicRot/AutoAmethyst/releases)** page
2. Place the file into your `rusherhacks/plugins` directory
3. Launch Minecraft with RusherHack installed
4. Enable **AutoAmethyst** from the module list

> [!WARNING]  
> This module is **strictly limited** to amethyst buds and clusters.  
> **Budding Amethyst blocks are never broken.**

## License

MIT License © GarlicRot
