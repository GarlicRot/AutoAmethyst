<h1 align="center">AutoAmethyst</h1>

<h3 align="center">Automatically harvest amethyst shards without breaking Budding Amethyst</h3>

<p align="center">
  <img src="https://img.shields.io/github/downloads/GarlicRot/AutoAmethyst/total?label=Downloads" alt="GitHub Downloads (all assets, all releases)">
  <img src="https://img.shields.io/badge/Minecraft-1.21.4-62b47a?style=flat&logo=minecraft&logoColor=white" alt="Minecraft Version">
  <img src="https://img.shields.io/badge/%F0%9F%A7%84-Approved%20%E2%9C%94%EF%B8%8F-blue?style=flat" alt="🧄 Approved ✔️">
</p>

---

## Overview

**AutoAmethyst** automatically breaks amethyst buds and clusters attached to **Budding Amethyst** blocks, allowing you to farm shards efficiently **without ever destroying the budding block itself**.

---

## Features

- Automatically breaks **amethyst buds & clusters**
- **Never** breaks Budding Amethyst
- Vanilla reach & line-of-sight checks
- Stable packet-based breaking (no ghost mining)
- Optional pickaxe requirement
- Visual rendering for budding blocks and targets

---

## Settings

| Setting            | Description                                                  |
|--------------------|--------------------------------------------------------------|
| **Range**          | Scan radius around the player for Budding Amethyst           |
| **Require Pickaxe**| Only run when holding a pickaxe                              |
| **Retry Cooldown** | Delay before retrying the same shard target                  |
| **Line of Sight**  | Only break shards you can directly see                       |
| **Swing**          | Swing the hand when breaking (visual only)                   |
| **Render**         | Render Budding Amethyst and target shards                    |
| **Budding Color**  | Color used to render Budding Amethyst blocks                 |
| **Target Color**   | Color used to render shard targets                           |
| **Line Width**     | Width of rendered block outlines                             |

---

## Installation

1. Download the latest `.jar` from the  
   **[Releases](https://github.com/GarlicRot/AutoAmethyst/releases)** page.
2. Place the file into your `rusherhacks/plugins` directory.
3. Launch Minecraft with RusherHack installed.
4. Enable **AutoAmethyst** from the module list.

---

## Notes

> [!NOTE]  
> AutoAmethyst intentionally uses vanilla reach and avoids client-side mining state to prevent desync, ghost blocks, and swing-only behavior.

> [!WARNING]  
> This module is designed for **amethyst buds and clusters only**. It will never break Budding Amethyst blocks.

---

## License

MIT License © GarlicRot
