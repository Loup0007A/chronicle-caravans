# Chronicle Caravans

**Living trade caravans that travel between the milestones you place, along the roads you build.**

A Minecraft mod for **Forge 1.20.1**, **Forge 1.21.1**, **NeoForge 1.21.1** and **NeoForge 26.3** (install it on the client and on the server).

Place a **Caravan Milestone** at your base. A few minutes later, a caravan appears on the horizon: a caravan master leading a draft llama hitched to a **covered wagon**, pack llamas trailing behind, loaded with goods from a far-away region. It walks to your milestone, halts to trade, then sets off for the next milestone. Build a road between the two and the caravan will follow it, and move faster.

## How it works

| | |
|---|---|
| **Caravan Milestone** | Lantern + emerald + 3 stone bricks. A lit stone marker: every milestone is a stop. Two milestones within 320 blocks form a trade route. Right-click it to see when the next caravan is due. |
| **The wagon** | A solid covered wagon with rolling spoked wheels, cargo and a glowing lantern; its canopy shows the colour of the caravan's region. The whole train walks in the master's footsteps. Right-click the master or the wagon to open the caravan's interface. |
| **Caravan interface** | Four tabs: **Market** (click to buy, shift-click to buy all you can), **Requests** (goods the caravan pays for), **Services** (escort, road crews) and **Guild** (skill tree). |
| **Villages** | Every village bell is a stop too: caravans travel between villages on their own, with no milestone needed, and follow the village paths. Villages and milestones less than 320 blocks apart form one network. |
| **Roads** | Caravans prefer walking on dirt paths, cobblestone, stone bricks, planks, gravel and more (block tag `chroniclecaravans:roads`), and move 30% faster on them. |
| **Regional goods** | Eight home regions: Dunes, Frostlands, Jungle, Highlands, Coast, Marshes, Woodlands, Steppe, each with a dozen specialities, barters, three requests and treasures (max-level enchanted books, rarities). A caravan never comes from the kind of region it is visiting. |
| **Guild and skills** | Guild experience: 1 per 10 emeralds traded, hosting (+2), bandits (+2 each), rescues (+4), inns (+2), roads built (+5). 12 levels, each unlocking rarer goods and giving a skill point. Skill tree in three branches: **Trader** (Haggler, Fair Seller, Hidden Stock, Merchant Prince), **Protector** (Veteran Guards, Bounty Hunter, Warden, Sworn Shield), **Builder** (Foreman, Paved Roads, Innkeeper, Cartographer). Your title follows your skills. Hurting a caravan costs experience. |
| **Escorts** | Hire guards for a caravan (14 emeralds: 2 guards for 10 minutes). They follow it and fight monsters and bandits. |
| **Road crews** | From a stop, hire builders who lay the road to a linked stop (dirt path, plank bridges, trees cleared). Price: about one emerald per 8 blocks. |
| **Inns** | Inn Sign (oak sign + bed + emerald) near a bed: at night, caravans nearby sleep there, safe from bandits, then restock and pay the innkeeper (into a chest or barrel next to the sign). |
| **Caravan Atlas** | Map + emerald. A map of the stops, inns and routes; rename your milestones and routes from it. Cartographers also see the caravans. |
| **Ambushes** | While you are near a caravan, bandits may attack it. Defeat them all and the master leaves you emeralds and a gift from its region. |
| **Hire a caravan** | Use an Emerald Block on a milestone to call a caravan right away (10 min cooldown): the nearest free caravan comes. |
| **Fewer, travelling caravans** | Caravans go from stop to stop (and to villages). A waiting stop gets a free caravan before a new one is created; new caravans are rare and appear out of sight. |
| **Milestones** | Broken, a milestone gives back its lantern. With Silk Touch, you keep it, and it keeps its schedule. |

## Caravan Guide

Like Create's Ponder: hold **[W]** while hovering the Caravan Milestone in your inventory (or type `/caravanguide`). Eight animated 3D scenes explain milestones, routes, the caravan, villages, ambushes and the guild, hiring, inns, road crews and escorts. Drag to turn the scene, scroll to zoom, click the timeline to jump.

## Commands

- `/caravans` — your guild level, title and skill points
- `/caravans stops` — every milestone
- `/caravans rename stop <pos> <name>` and `/caravans rename route <pos> <pos> <name>` — what the atlas uses
- `/caravanguide [scene]` — the Caravan Guide (client)
- Operators: `/caravans summon [region]`, `/caravans ambush`, `/caravans atlas`, `/caravans reputation <player> <xp>`

## Configuration

`config/chroniclecaravans-common.toml`: time between visits, halt duration, maximum number of caravans, time between brand new caravans, number of llamas, road preference and speed bonus, ambush chance, reputation and chat announcements. Each feature can be switched off.

Modpack makers: add any block to the `chroniclecaravans:roads` block tag with a datapack to make it count as a road.

## Versions

| Minecraft | Loader | Java |
|---|---|---|
| 1.20.1 | Forge | 17 |
| 1.21.1 | Forge | 21 |
| 1.21.1 | NeoForge | 21 |
| 26.3 | NeoForge | 25 |

## Download

The latest jar of each version is in [`releases/`](releases/) (`chroniclecaravans-latest-<loader>-<version>.jar`) and on the [Releases page](https://github.com/Loup0007A/chronicle-caravans/releases).

## Français

Posez une **borne de caravane** (lanterne + émeraude + 3 pierres taillées) dans votre base. Les caravanes passent aussi d'elles-mêmes de village en village, sans borne, en suivant les chemins. Quelques minutes plus tard, une caravane arrive : un maître caravanier, un lama de trait attelé à un chariot bâché et des lamas de bât, chargés de marchandises d'une région lointaine. Elle s'arrête pour commercer, puis repart vers la borne suivante en suivant les routes que vous construisez. Commercez, accueillez et défendez les caravanes contre les bandits pour monter en rang auprès des guildes et débloquer des marchandises plus rares. Maintenez [W] sur la borne dans l'inventaire pour ouvrir le guide animé (façon Ponder de Create).

## License

MIT
