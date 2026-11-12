# Changelog

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
