# Chronicle Caravans

**Living trade caravans that travel between the milestones you place, along the roads you build.**

A Minecraft mod for **Forge 1.20.1**, **Forge 1.21.1**, **NeoForge 1.21.1** and **NeoForge 26.3** (install it on the client and on the server).

Place a **Caravan Milestone** at your base. A few minutes later, a caravan appears on the horizon: a caravan master leading a draft llama hitched to a **covered wagon**, pack llamas trailing behind, loaded with goods from a far-away region. It walks to your milestone, halts to trade, then sets off for the next milestone. Build a road between the two and the caravan will follow it, and move faster.

## How it works

| | |
|---|---|
| **Caravan Milestone** | Lantern + emerald + 3 stone bricks (in the recipe book once you hold an emerald). Placing one unlocks the recipes of the atlas, the inn sign, the loading dock, the trading post, the hitching post and the relay charter. A lit stone marker: every milestone is a stop. Two milestones within 320 blocks form a trade route. Right-click it to see when the next caravan is due. |
| **The wagon** | A solid covered wagon with rolling spoked wheels, cargo and a glowing lantern; its canopy shows the colour of the caravan's region. The whole train walks in the master's footsteps. Right-click the master or the wagon to open the caravan's interface. |
| **Caravan interface** | A trader's ledger in five tabs: **Market** (click to buy, shift-click to buy all you can), **Requests** (goods the caravan pays for), **Freight** (send parcels), **Services** (escort, road crews) and **Guild** (skill tree). The master's portrait, the market news of the day, and the price changes on every offer. |
| **Villages** | Every village bell is a stop too: caravans travel between villages on their own, with no milestone needed, and follow the village paths. Villages and milestones less than 320 blocks apart form one network. |
| **Roads** | Caravans prefer walking on dirt paths, cobblestone, stone bricks, planks, gravel and more (block tag `chroniclecaravans:roads`), and move 30% faster on them. |
| **Regional goods** | Eight home regions: Dunes, Frostlands, Jungle, Highlands, Coast, Marshes, Woodlands, Steppe, each with a dozen specialities, barters, three requests and treasures (max-level enchanted books, rarities). A caravan never comes from the kind of region it is visiting. |
| **Guild and skills** | Guild experience: 1 per 10 emeralds traded, hosting (+2), bandits (+2 each), rescues (+4), inns (+2), roads built (+5). 12 levels, each unlocking rarer goods and giving a skill point. Skill tree in three branches: **Trader** (Haggler, Fair Seller, Hidden Stock, Merchant Prince), **Protector** (Veteran Guards, Bounty Hunter, Warden, Sworn Shield), **Builder** (Foreman, Paved Roads, Innkeeper, Cartographer). Your title follows your skills. Hurting a caravan costs experience. |
| **Escorts** | Hire guards for a caravan (14 emeralds: 2 guards for 10 minutes). They follow it and fight monsters and bandits. |
| **Road crews** | From a stop, hire builders who lay the road to a linked stop (dirt path, plank bridges, trees cleared). Price: about one emerald per 8 blocks. |
| **Inns** | Inn Sign (oak sign + bed + emerald) near a bed: at night, caravans nearby park their train and sleep in the beds, safe from bandits, then restock and pay the innkeeper (into a chest or barrel next to the sign). |
| **Caravan Atlas** | Map + emerald. A map of the stops, inns and routes; rename your milestones and routes from it. Cartographers also see the caravans. |
| **Ambushes** | While you are near a caravan, bandits may attack it. Defeat them all and the master leaves you emeralds and a gift from its region. |
| **Hire a caravan** | Use an Emerald Block on a milestone to call a caravan right away (10 min cooldown): the nearest free caravan comes. |
| **Fewer, travelling caravans** | Caravans go from stop to stop (and to villages). A waiting stop gets a free caravan before a new one is created; new caravans are rare and appear out of sight. |
| **Milestones** | Broken, a milestone gives back its lantern. With Silk Touch, you keep it, and it keeps its schedule. |
| **Supply and demand** | Every deal moves the market: goods sold a lot are paid less, goods bought a lot cost more; it fades by half every day. Each region has its news of the day: a shortage (+50% for one request) and a surplus (one good 25% cheaper). |
| **Weather and camps** | Rain slows caravans by a quarter. In a thunderstorm, or at night with no inn within reach, they camp beside the road around a campfire. |
| **Freight** | From the Freight tab of a halted caravan, send a parcel to any stop of the network: it goes from stop to stop, over several legs if needed (1 emerald + 1 per leg), and lands in a dock or a chest next to the destination's milestone. |
| **Loading Dock** | Barrel + hopper + lead. Near a milestone; named after a stop (anvil), it ships what you put in with the next caravan, paid with its emeralds; unnamed, it receives the deliveries. Hoppers fill and empty it. |
| **Trading Post** | Chest + emerald + comparator + wool + slab. Trades on its own with the caravans halting nearby: sells them what they ask for from its stock, then buys what its orders show. Hoppers in and out, comparator output, a redstone signal pauses it. |
| **Redstone** | A comparator next to a milestone reads 15 while a caravan halts there, 1 to 7 while one is coming. A redstone pulse on a milestone calls a caravan, paid with an emerald block from a chest or barrel beside it. |
| **Relay Charter** | Paper + emerald block + gold ingot + honeycomb. Use it on a milestone to raise it one tier (up to 4): a tier 2, 3 or 4 relay takes 2, 3 or 4 caravans at once, and caravans come 3, 6 or 10 times as often. The milestone shows its tier (gold collar, guild pennants); Silk Touch keeps it. |
| **Hitching Post** | Wooden fence + iron ingot. A parking place for one train. A relay needs one post within 16 blocks for each caravan beyond the first: when the main spot is taken, the next caravan parks at a free post, ties its draft llama to it and walks to the milestone. Leads tie to it like to a fence. |
| **Parking at the inn** | At an inn, the caravan parks its train at a hitching post near the inn, or on open ground off the road, then the master walks to a free bed and sleeps in it until morning (doors open for him). A caravan at an inn still counts for the stop it serves, and no new caravan sets out at night. |

## Caravan Guide

Like Create's Ponder: hold **[W]** while hovering the Caravan Milestone, a Loading Dock or a Trading Post in your inventory (or type `/caravanguide`). Eleven animated 3D scenes explain milestones, routes, the caravan, villages, ambushes and the guild, hiring, inns, road crews and escorts, weather and camps, freight and docks, redstone and trading posts. Drag to turn the scene, scroll to zoom, click the timeline to jump.

## Commands

- `/caravans` — your guild level, title and skill points
- `/caravans stops` — every milestone
- `/caravans rename stop <pos> <name>` and `/caravans rename route <pos> <pos> <name>` — what the atlas uses
- `/caravans dock <address>` — the address of the open loading dock (`-`: receiving), what its screen uses
- `/caravanguide [scene]` — the Caravan Guide (client)
- Operators: `/caravans summon [region]`, `/caravans ambush`, `/caravans atlas`, `/caravans reputation <player> <xp>`

## Configuration

`config/chroniclecaravans-common.toml`: time between visits, halt duration, maximum number of caravans, time between brand new caravans, number of llamas, road preference and speed bonus, ambush chance, reputation, chat announcements, weather and camps, market prices, freight, trading posts, redstone, relays (and their highest tier) and parking. Each feature can be switched off.

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

Posez une **borne de caravane** (lanterne + émeraude + 3 pierres taillées) dans votre base : la poser débloque dans le livre de recettes l'atlas, l'enseigne d'auberge, le quai de chargement, le comptoir de commerce, le poteau d'attelage et la charte de relais. Les caravanes passent aussi d'elles-mêmes de village en village, sans borne, en suivant les chemins. Quelques minutes plus tard, une caravane arrive : un maître caravanier, un lama de trait attelé à un chariot bâché et des lamas de bât, chargés de marchandises d'une région lointaine. Elle s'arrête pour commercer, puis repart vers la borne suivante en suivant les routes que vous construisez. Commercez, accueillez et défendez les caravanes contre les bandits pour monter en rang auprès des guildes et débloquer des marchandises plus rares. Envoyez des colis par caravane, automatisez vos échanges avec les quais de chargement, les comptoirs de commerce et la redstone ; les prix suivent l'offre et la demande, et les caravanes campent sous l'orage. À l'auberge, le maître range son convoi hors de la route et dort dans un lit. Une charte de relais améliore la borne : avec des poteaux d'attelage autour, elle accueille jusqu'à quatre caravanes à la fois. Maintenez [W] sur la borne dans l'inventaire pour ouvrir le guide animé (façon Ponder de Create).

## License

MIT
