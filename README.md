# Chronicle Caravans

**Living trade caravans that travel between the milestones you place, along the roads you build.**

A Minecraft mod for **NeoForge 1.21.1** (install it on the client and on the server).

Place a **Caravan Milestone** at your base. A few minutes later, a caravan appears on the horizon: a caravan master leading a draft llama hitched to a **covered wagon**, pack llamas trailing behind, loaded with goods from a far-away region. It walks to your milestone, halts to trade, then sets off for the next milestone. Build a road between the two and the caravan will follow it, and move faster.

## How it works

| | |
|---|---|
| **Caravan Milestone** | Lantern + emerald + 3 stone bricks. A lit stone marker: every milestone is a stop. Two milestones within 320 blocks form a trade route. Right-click it to see when the next caravan is due. |
| **The wagon** | A covered wagon with rolling spoked wheels, cargo and a glowing lantern; its canopy shows the colour of the caravan's region. The whole train walks in the master's footsteps. Right-click the wagon to trade. |
| **Roads** | Caravans prefer walking on dirt paths, cobblestone, stone bricks, planks, gravel and more (block tag `chroniclecaravans:roads`), and move 30% faster on them. |
| **Regional goods** | Eight home regions: Dunes, Frostlands, Jungle, Highlands, Coast, Marshes, Woodlands, Steppe. A caravan never comes from the kind of region it is visiting, so it always brings things you cannot find nearby. |
| **Reputation** | Trade (+1), host a caravan at your milestone (+3), defeat bandits (+2 each) and save a caravan (+5). Five ranks: Stranger, Acquaintance (10), Partner (30), Patron (75), Legend (150). Each rank unlocks rarer offers; Patrons and Legends get 15% and 25% off. Hurting a caravan costs reputation. |
| **Ambushes** | While you are near a caravan, bandits may attack it. Defeat them all and the master leaves you emeralds and a gift from its region. |
| **Hire a caravan** | Use an Emerald Block on a milestone to call a caravan right away (10 min cooldown). |

## Caravan Guide

Like Create's Ponder: hold **[W]** while hovering the Caravan Milestone in your inventory (or type `/caravanguide`). Five animated 3D scenes explain milestones, routes, the caravan, ambushes and reputation, and hiring. Drag to turn the scene, scroll to zoom, click the timeline to jump.

## Commands

- `/caravans` — your reputation and rank
- `/caravans stops` — every milestone
- `/caravanguide [scene]` — the Caravan Guide (client)
- Operators: `/caravans summon [region]`, `/caravans ambush`, `/caravans reputation <player> <value>`

## Configuration

`config/chroniclecaravans-common.toml`: time between visits, halt duration, maximum number of caravans, number of llamas, road preference and speed bonus, ambush chance, reputation and chat announcements. Each feature can be switched off.

Modpack makers: add any block to the `chroniclecaravans:roads` block tag with a datapack to make it count as a road.

## Download

The latest jar is in [`releases/`](releases/) and on the [Releases page](https://github.com/Loup0007A/chronicle-caravans/releases).

## Français

Posez une **borne de caravane** (lanterne + émeraude + 3 pierres taillées) dans votre base. Quelques minutes plus tard, une caravane arrive : un maître caravanier, un lama de trait attelé à un chariot bâché et des lamas de bât, chargés de marchandises d'une région lointaine. Elle s'arrête pour commercer, puis repart vers la borne suivante en suivant les routes que vous construisez. Commercez, accueillez et défendez les caravanes contre les bandits pour monter en rang auprès des guildes et débloquer des marchandises plus rares. Maintenez [W] sur la borne dans l'inventaire pour ouvrir le guide animé (façon Ponder de Create).

## License

MIT
