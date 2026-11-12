# Changelog

## 1.1.0

- **New caravan interface**, in four tabs: **Market** (one click to buy, shift-click to buy as much as you can; no more dragging items into slots), **Requests** (goods the caravan pays emeralds for, with how many you have), **Services** and **Guild**.
- **Much better trades**: every region now sells a dozen real specialities in good quantities (32 sandstone, 16 blue ice, 10 iron ingots, diamonds, tridents, totems, heart of the sea...), barters (16 raw iron + 2 emeralds → 2 iron blocks...), asks for 3 goods it lacks, and hides **treasures**: max-level enchanted books (Mending, Fortune III, Efficiency V...) and rarities (sniffer egg, echo shards...). Requests restock at every stop.
- **Guild and skill tree** instead of the old ranks: guild experience gives levels (12), each level a skill point, to spend in three branches of four skills: **Trader** (discounts, better prices for requests, hidden stock), **Protector** (veteran guards, bounties, longer escorts, a free guard at your milestones), **Builder** (faster crews, paved roads with lamp posts, richer inns, caravans on the atlas). Your **title** shows what you do: *Merchant*, *Road Warden*, *Master Builder*...
- **Slower reputation**: one experience point per 10 emeralds traded (instead of one per trade), level 6 needs 150 xp.
- **Escorts**: hire guards for a caravan (14 emeralds, 2 guards for 10 minutes): they follow it and fight off monsters and bandits.
- **Road crews**: from a stop, hire builders who lay the road to the next stop for you (dirt path, plank bridges over water, trees cleared; paved in stone bricks with the skill).
- **Inns**: put up an **Inn Sign** (sign + bed + emerald) near a bed. At nightfall, caravans nearby come to sleep there, safe from bandits; in the morning they restock and pay the innkeeper in the chest or barrel next to the sign.
- **Caravan Atlas** (map + emerald): a map of the stops, inns and routes around you. **Rename** your milestones and the routes from it.
- **Fewer caravans out of nowhere**: caravans now travel from stop to stop. A stop that waits gets a free caravan nearby before a new one is ever created; a caravan asleep in faraway land is moved where it is needed; brand new caravans are rare (`newCaravanCooldownSeconds`) and appear out of sight. Caravans on a circuit head for villages too.
- **A real hitbox for the wagon**: it is solid (you can no longer walk through it), stays on the ground and only tilts gently. The caravan master no longer runs away or gets thrown around when hit.
- **Milestones**: breaking one gives back its lantern only. With **Silk Touch** you get the milestone back, and it keeps its schedule (picking it up and placing it again no longer brings a caravan sooner).
- Two new guide scenes: inns, road crews and escorts (eight scenes in all).

## 1.0.0

- **Caravans visit villages**: every village bell is now a caravan stop, with no milestone needed. Caravans travel between nearby villages and your milestones as one network of trade routes, and follow the village paths. Switch it off with `villageStops` in the config.
- Caravans arrive on a road when there is one, and never appear on a roof.
- New guide scene: "Villages" (six scenes in all).
- **Now on four versions**: NeoForge 1.21.1, Forge 1.21.1, Forge 1.20.1 and NeoForge 26.3.

## 0.2.0

- **A real caravan**: the master now leads a draft llama hitched to a **covered wagon** (new 3D model): spoked wheels with iron tyres that roll as it moves, wooden hoops, cargo crates and barrels, a lantern glowing at the front. The canopy takes the colour of the caravan's home region.
- Pack llamas follow the wagon on leads. The whole train walks exactly in the master's footsteps, so it stays on your roads, even around corners and up slopes; the wagon tilts with the ground.
- Right-click the wagon to trade with the caravan master.
- **Caravan Guide**, in the style of Create's Ponder: hold **[W]** while hovering the Caravan Milestone in your inventory. Five animated 3D scenes (the milestone, trade routes, the caravan, ambushes and reputation, hiring), with text bubbles, a timeline, and a scene you can turn and zoom. Also `/caravanguide [scene]`.
- New config options: `wagonEnabled`; `minLlamas` / `maxLlamas` now count the pack llamas behind the wagon (default 1 to 2).

## 0.1.0

First release (NeoForge 1.21.1).

- **Caravan Milestone**: a lit stone marker (lantern + emerald + 3 stone bricks). Each milestone is a stop for caravans; two milestones within 320 blocks form a trade route.
- **Caravans**: a caravan master leading a line of 2 to 4 pack llamas. They walk to the milestone, halt for three minutes, then set off for the next one.
- **Roads**: caravans prefer road blocks (dirt path, cobblestone, stone bricks, planks, gravel... block tag `chroniclecaravans:roads`) and move 30% faster on them.
- **Regional goods**: 8 home regions (Dunes, Frostlands, Jungle, Highlands, Coast, Marshes, Woodlands, Steppe), each with its own stock. A caravan never comes from the region it visits.
- **Reputation**: trade, host and defend caravans to climb 5 ranks (Stranger → Legend). Higher ranks unlock rarer offers and discounts.
- **Ambushes**: bandits may attack a caravan while you are nearby. Defeat them for reputation, emeralds and a gift.
- **Hire a caravan**: use an Emerald Block on a milestone to call one right away.
- Commands: `/caravans`, `/caravans stops`, and for operators `/caravans summon [region]`, `/caravans ambush`, `/caravans reputation <player> <value>`.
- Every feature can be tuned or switched off in `config/chroniclecaravans-common.toml`.
- English and French.
