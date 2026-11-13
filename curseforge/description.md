# Fiche CurseForge — textes prêts à copier

## Name

Chronicle Caravans

## Summary

Living trade caravans travel between villages and the milestones you place, following your roads. Covered wagons, regional goods, freight, a living market, automation and an animated guide.

## Description (Markdown)

### Your roads finally lead somewhere.

Place a **Caravan Milestone** at your base. A few minutes later, a caravan appears on the horizon: a caravan master leading a draft llama hitched to a **covered wagon**, pack llamas trailing behind, loaded with goods from a far-away region. It walks to your milestone, halts to trade, then sets off for the next one.

Placing your first milestone also unlocks the mod's other recipes in your recipe book: the atlas, the inn sign, the loading dock, the trading post, the hitching post and the relay charter.

Place a second milestone, build a road between the two, and you have a **trade route**: caravans follow your road, and move faster on it.

### ✦ A real caravan
- A **covered wagon** with spoked wheels that roll as it moves, cargo crates and barrels, and a lantern glowing at the front.
- The canopy takes the **colour of the caravan's home region**: you can tell where it comes from before it arrives.
- The whole train walks **in the master's footsteps**: it stays on your roads around corners and up slopes.
- The wagon is **solid**, stays on the ground, and the train no longer jumps around when attacked.
- Right-click the master or the wagon to open the **caravan interface**, a hand-drawn trader's ledger: Market (one click to buy, shift-click to buy all), Requests, Freight, Services and Guild.

### ✦ A guide in the style of Create's Ponder
Hold **[W]** over the Caravan Milestone, a Loading Dock or a Trading Post in your inventory: eleven animated 3D scenes show how everything works, with text bubbles and a timeline. Turn the scene, zoom in, replay. Or type `/caravanguide`.

### ✦ Villages come alive
Every village bell is a caravan stop: caravans travel between villages **on their own**, with no milestone to build. Village paths are already roads, so they follow them from one village to the next. Your milestones join the same network.

### ✦ Milestones and roads
- **Caravan Milestone**: lantern + emerald + 3 stone bricks. A lit stone marker, and a stop for every caravan. Right-click it to see when the next one is due.
- Two milestones within 320 blocks are linked: caravans travel from one to the other.
- Caravans **prefer roads** (dirt path, cobblestone, stone bricks, planks, gravel and more) and walk **30% faster** on them. Any block can become a road with the `chroniclecaravans:roads` block tag.
- In a hurry? Use an **Emerald Block** on a milestone to hire a caravan right away.
- Broken, a milestone gives back its lantern; with **Silk Touch** you keep it, schedule included.
- Caravans **travel** from stop to stop instead of popping up everywhere: a waiting stop gets a free caravan nearby before a new one is ever created.

### ✦ Road crews, escorts and inns
- **Road crews**: from a stop, pay builders who lay the road to the next stop for you — dirt path, plank bridges over rivers, trees cleared.
- **Escorts**: hire guards who walk with a caravan and fight off monsters and bandits.
- **Inns**: put up an **Inn Sign** near a bed. At nightfall caravans come to your inn: the master **parks his train off the road** (or at a hitching post), walks in and **sleeps in a bed**, safe from bandits. In the morning he walks back to his train and pays you.
- **Relays**: use a **Relay Charter** on a milestone to upgrade it, up to tier 4. Place a **Hitching Post** around it for each extra caravan: when the main spot is taken, the next caravan parks its train at a post, ties its llama and walks to the milestone. A tier 4 relay hosts four caravans at once, and they come much more often. The milestone grows a gold collar and guild pennants.

### ✦ A living world
- **Supply and demand**: sell a lot of one good and the caravans pay less for it, buy a lot and prices rise; the market calms down day after day. Every region has its **news of the day**: a shortage paid 50% more, a surplus sold 25% cheaper.
- **Weather**: rain slows the caravans; in a thunderstorm, or at night far from any inn, they **make camp** beside the road around a campfire.
- **Freight**: from the Freight tab of a halted caravan, send a **parcel** to any stop of the network. It travels from stop to stop, over several legs if needed, and lands in a chest or a dock at its destination.

### ✦ Automation for the engineers
- **Loading Dock**: name it after a stop with an anvil, fill it (hoppers welcome) with goods and a few emeralds: the next caravan ships it all there. An unnamed dock receives the deliveries, and a hopper below empties it. Build real supply chains between your bases!
- **Trading Post**: a market stall that **trades on its own** with the caravans halting nearby: it sells them what they ask for from its stock, then buys what its orders show. Hoppers in and out, a comparator reads the takings, redstone pauses it.
- **Redstone milestones**: a comparator next to a milestone reads **15** while a caravan halts there and **1 to 7** while one is coming. A redstone pulse **calls a caravan**, paid with an emerald block from the chest beside it.
- Docks and trading posts work with the pipes and funnels of other mods.

### ✦ The Caravan Atlas
A parchment map of the stops, inns and trade routes around you. **Rename** your milestones and your roads; cartographers also see the caravans on the move.

### ✦ Goods from eight regions
Dunes, Frostlands, Jungle, Highlands, Coast, Marshes, Woodlands, Steppe: every caravan carries a dozen specialities of its home region in real quantities, barters, **requests** (goods it pays emeralds for) and **treasures**: max-level enchanted books, tridents, totems, sniffer eggs... It never comes from the kind of place it is visiting.

### ✦ The caravan guilds and their skill tree
Trade with caravans, host them, protect them and build roads: you gain **guild levels** that unlock rarer goods, and **skill points** to spend in three branches — **Trader**, **Protector** and **Builder** (12 skills). Your **title** tells what you do: *Peddler*, *Merchant Prince*, *Road Warden*, *Master Builder*... Attack a caravan and the guilds remember.

### ✦ Ambushes
While you are near a caravan, bandits may attack it. Defeat them all and the caravan master leaves you emeralds and a gift from its region.

### ✦ Made for servers and modpacks
- Every feature has its own switch or dial in `config/chroniclecaravans-common.toml`: time between visits, number of caravans and llamas, how often new caravans appear, road bonus, ambush chance, reputation, chat messages, weather, market, freight, trading posts, redstone.
- Light on performance: a few caravans at a time, nothing runs when no milestone exists.
- Commands for admins and creators: `/caravans summon [region]`, `/caravans ambush`, `/caravans atlas`, `/caravans reputation <player> <xp>`.
- English and French.

**Forge 1.20.1 · Forge 1.21.1 · NeoForge 1.21.1 · NeoForge 26.3.** Install on both the client and the server.

Feel free to include it in any modpack.
