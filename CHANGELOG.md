# Changelog

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
