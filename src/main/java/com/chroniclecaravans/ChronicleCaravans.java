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
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
//#if neoforge
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
//#else
//$$ import net.minecraftforge.fml.ModLoadingContext;
//$$ import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//$$ import net.minecraftforge.registries.DeferredRegister;
//$$ import net.minecraftforge.registries.ForgeRegistries;
//$$ import net.minecraftforge.registries.RegistryObject;
//$$ import net.minecraft.world.item.Item;
//#endif
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Chronicle Caravans: living trade caravans that travel between the milestones players build. */
@Mod(ChronicleCaravans.MODID)
public class ChronicleCaravans {
    public static final String MODID = "chroniclecaravans";
    public static final Logger LOG = LoggerFactory.getLogger("ChronicleCaravans");

    private static final BlockBehaviour.Properties MILESTONE_PROPS = BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0F, 6.0F)
            .requiresCorrectToolForDrops()
            .sound(SoundType.STONE)
            .lightLevel(s -> 12)
            .pushReaction(PushReaction.BLOCK)
            .noOcclusion();

    //#if neoforge
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredBlock<MilestoneBlock> MILESTONE = BLOCKS.registerBlock("milestone", MilestoneBlock::new, MILESTONE_PROPS);
    public static final DeferredItem<BlockItem> MILESTONE_ITEM = ITEMS.registerSimpleBlockItem("milestone", MILESTONE);
    public static final DeferredHolder<EntityType<?>, EntityType<CaravanMaster>> CARAVAN_MASTER = ENTITIES.register("caravan_master",
            () -> masterType().build(entityKey("caravan_master")));
    public static final DeferredHolder<EntityType<?>, EntityType<CaravanWagon>> WAGON = ENTITIES.register("wagon",
            () -> CaravanWagon.builder().build(entityKey("wagon")));
    //#else
    //$$ public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    //$$ public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    //$$ public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    //$$
    //$$ public static final RegistryObject<MilestoneBlock> MILESTONE = BLOCKS.register("milestone", () -> new MilestoneBlock(MILESTONE_PROPS));
    //$$ public static final RegistryObject<BlockItem> MILESTONE_ITEM = ITEMS.register("milestone", () -> new BlockItem(MILESTONE.get(), new Item.Properties()));
    //$$ public static final RegistryObject<EntityType<CaravanMaster>> CARAVAN_MASTER = ENTITIES.register("caravan_master",
    //$$         () -> masterType().build(entityKey("caravan_master")));
    //$$ public static final RegistryObject<EntityType<CaravanWagon>> WAGON = ENTITIES.register("wagon",
    //$$         () -> CaravanWagon.builder().build(entityKey("wagon")));
    //#endif

    private static EntityType.Builder<CaravanMaster> masterType() {
        EntityType.Builder<CaravanMaster> b = EntityType.Builder.<CaravanMaster>of(CaravanMaster::new, MobCategory.CREATURE)
                .sized(0.6F, 1.95F)
                .clientTrackingRange(10);
        //#if !mc120
        b = b.eyeHeight(1.62F);
        //#endif
        return b;
    }

    /** What {@code EntityType.Builder.build} wants: a plain id before 1.21.2, a registry key after. */
    //#if mc26
    //$$ private static net.minecraft.resources.ResourceKey<EntityType<?>> entityKey(String name) {
    //$$     return net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id(name));
    //$$ }
    //#else
    private static String entityKey(String name) {
        return MODID + ":" + name;
    }
    //#endif

    /** Blocks a caravan treats as a road: it prefers walking on them and moves faster there. */
    public static final TagKey<Block> ROADS = TagKey.create(Registries.BLOCK, id("roads"));

    //#if neoforge
    public ChronicleCaravans(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CCConfig.SPEC);
        init(modBus);
    }
    //#else
    //$$ public ChronicleCaravans() {
    //$$     ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CCConfig.SPEC);
    //$$     init(FMLJavaModLoadingContext.get().getModEventBus());
    //$$ }
    //#endif

    private void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
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
