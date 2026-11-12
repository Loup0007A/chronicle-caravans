package com.chroniclecaravans;

import com.chroniclecaravans.block.MilestoneBlock;
import com.chroniclecaravans.entity.CaravanMaster;
import com.chroniclecaravans.entity.CaravanWagon;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Chronicle Caravans: living trade caravans that travel between the milestones players build. */
@Mod(ChronicleCaravans.MODID)
public class ChronicleCaravans {
    public static final String MODID = "chroniclecaravans";
    public static final Logger LOG = LoggerFactory.getLogger("ChronicleCaravans");

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredBlock<MilestoneBlock> MILESTONE = BLOCKS.register("milestone",
            () -> new MilestoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .lightLevel(s -> 12)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()));
    public static final DeferredItem<BlockItem> MILESTONE_ITEM = ITEMS.registerSimpleBlockItem("milestone", MILESTONE);

    public static final DeferredHolder<EntityType<?>, EntityType<CaravanMaster>> CARAVAN_MASTER = ENTITIES.register("caravan_master",
            () -> EntityType.Builder.<CaravanMaster>of(CaravanMaster::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build(MODID + ":caravan_master"));

    public static final DeferredHolder<EntityType<?>, EntityType<CaravanWagon>> WAGON = ENTITIES.register("wagon",
            () -> CaravanWagon.builder().build(MODID + ":wagon"));

    /** Blocks a caravan treats as a road: it prefers walking on them and moves faster there. */
    public static final TagKey<Block> ROADS = TagKey.create(Registries.BLOCK, id("roads"));

    public ChronicleCaravans(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, CCConfig.SPEC);
        modBus.addListener(this::attributes);
        modBus.addListener(this::creativeTab);
    }

    private void attributes(EntityAttributeCreationEvent e) {
        e.put(CARAVAN_MASTER.get(), CaravanMaster.createAttributes().build());
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) e.accept(MILESTONE_ITEM.get());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
