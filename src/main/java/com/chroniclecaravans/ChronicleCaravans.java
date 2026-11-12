package com.chroniclecaravans;

import com.chroniclecaravans.block.InnSignBlock;
import com.chroniclecaravans.block.MilestoneBlock;
import com.chroniclecaravans.entity.CaravanGuard;
import com.chroniclecaravans.entity.CaravanMaster;
import com.chroniclecaravans.entity.CaravanWagon;
import com.chroniclecaravans.entity.RoadBuilder;
import com.chroniclecaravans.item.AtlasItem;
import com.chroniclecaravans.menu.AtlasMenu;
import com.chroniclecaravans.menu.CaravanMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
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

    private static BlockBehaviour.Properties milestoneProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(2.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE)
                .lightLevel(s -> 12)
                .pushReaction(PushReaction.BLOCK)
                .noOcclusion();
    }

    private static BlockBehaviour.Properties innProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.0F)
                .sound(SoundType.WOOD)
                .ignitedByLava()
                .noOcclusion();
    }

    //#if mc26
    //$$ private static final java.util.function.Supplier<BlockBehaviour.Properties> PROPS = ChronicleCaravans::milestoneProps;
    //$$ private static final java.util.function.Supplier<BlockBehaviour.Properties> INN_PROPS = ChronicleCaravans::innProps;
    //$$ private static final java.util.function.UnaryOperator<net.minecraft.world.item.Item.Properties> SINGLE = p -> p.stacksTo(1);
    //#else
    private static final BlockBehaviour.Properties PROPS = milestoneProps();
    private static final BlockBehaviour.Properties INN_PROPS = innProps();
    private static final net.minecraft.world.item.Item.Properties SINGLE = new net.minecraft.world.item.Item.Properties().stacksTo(1);
    //#endif

    //#if neoforge
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredBlock<MilestoneBlock> MILESTONE = BLOCKS.registerBlock("milestone", MilestoneBlock::new, PROPS);
    public static final DeferredItem<BlockItem> MILESTONE_ITEM = ITEMS.registerSimpleBlockItem("milestone", MILESTONE);
    public static final DeferredHolder<EntityType<?>, EntityType<CaravanMaster>> CARAVAN_MASTER = ENTITIES.register("caravan_master",
            () -> masterType().build(entityKey("caravan_master")));
    public static final DeferredHolder<EntityType<?>, EntityType<CaravanWagon>> WAGON = ENTITIES.register("wagon",
            () -> CaravanWagon.builder().build(entityKey("wagon")));
    public static final DeferredHolder<EntityType<?>, EntityType<CaravanGuard>> GUARD = ENTITIES.register("caravan_guard",
            () -> humanType(CaravanGuard::new).build(entityKey("caravan_guard")));
    public static final DeferredHolder<EntityType<?>, EntityType<RoadBuilder>> BUILDER = ENTITIES.register("road_builder",
            () -> humanType(RoadBuilder::new).build(entityKey("road_builder")));
    public static final DeferredBlock<InnSignBlock> INN_SIGN = BLOCKS.registerBlock("inn_sign", InnSignBlock::new, INN_PROPS);
    public static final DeferredItem<BlockItem> INN_SIGN_ITEM = ITEMS.registerSimpleBlockItem("inn_sign", INN_SIGN);
    public static final DeferredItem<AtlasItem> ATLAS = ITEMS.registerItem("caravan_atlas", AtlasItem::new, SINGLE);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<CaravanMenu>> CARAVAN_MENU = MENUS.register("caravan",
            () -> new MenuType<>(CaravanMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<AtlasMenu>> ATLAS_MENU = MENUS.register("atlas",
            () -> new MenuType<>(AtlasMenu::new, FeatureFlags.DEFAULT_FLAGS));
    //#else
    //$$ public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    //$$ public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    //$$ public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    //$$
    //$$ public static final RegistryObject<MilestoneBlock> MILESTONE = BLOCKS.register("milestone", () -> new MilestoneBlock(milestoneProps()));
    //$$ public static final RegistryObject<BlockItem> MILESTONE_ITEM = ITEMS.register("milestone", () -> new BlockItem(MILESTONE.get(), new Item.Properties()));
    //$$ public static final RegistryObject<EntityType<CaravanMaster>> CARAVAN_MASTER = ENTITIES.register("caravan_master",
    //$$         () -> masterType().build(entityKey("caravan_master")));
    //$$ public static final RegistryObject<EntityType<CaravanWagon>> WAGON = ENTITIES.register("wagon",
    //$$         () -> CaravanWagon.builder().build(entityKey("wagon")));
    //$$ public static final RegistryObject<EntityType<CaravanGuard>> GUARD = ENTITIES.register("caravan_guard",
    //$$         () -> humanType(CaravanGuard::new).build(entityKey("caravan_guard")));
    //$$ public static final RegistryObject<EntityType<RoadBuilder>> BUILDER = ENTITIES.register("road_builder",
    //$$         () -> humanType(RoadBuilder::new).build(entityKey("road_builder")));
    //$$ public static final RegistryObject<InnSignBlock> INN_SIGN = BLOCKS.register("inn_sign", () -> new InnSignBlock(innProps()));
    //$$ public static final RegistryObject<BlockItem> INN_SIGN_ITEM = ITEMS.register("inn_sign", () -> new BlockItem(INN_SIGN.get(), new Item.Properties()));
    //$$ public static final RegistryObject<AtlasItem> ATLAS = ITEMS.register("caravan_atlas", () -> new AtlasItem(new Item.Properties().stacksTo(1)));
    //$$ public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    //$$ public static final RegistryObject<MenuType<CaravanMenu>> CARAVAN_MENU = MENUS.register("caravan",
    //$$         () -> new MenuType<>(CaravanMenu::new, FeatureFlags.DEFAULT_FLAGS));
    //$$ public static final RegistryObject<MenuType<AtlasMenu>> ATLAS_MENU = MENUS.register("atlas",
    //$$         () -> new MenuType<>(AtlasMenu::new, FeatureFlags.DEFAULT_FLAGS));
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

    private static <T extends net.minecraft.world.entity.Entity> EntityType.Builder<T> humanType(EntityType.EntityFactory<T> factory) {
        EntityType.Builder<T> b = EntityType.Builder.of(factory, MobCategory.MISC).sized(0.6F, 1.95F).clientTrackingRange(10);
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
        container.registerConfig(ModConfig.Type.COMMON, CCConfig.SPEC, MODID + "-common.toml");
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
        MENUS.register(modBus);
        modBus.addListener(this::attributes);
        modBus.addListener(this::creativeTab);
    }

    private void attributes(EntityAttributeCreationEvent e) {
        e.put(CARAVAN_MASTER.get(), CaravanMaster.createAttributes().build());
        e.put(GUARD.get(), CaravanGuard.createAttributes().build());
        e.put(BUILDER.get(), RoadBuilder.createAttributes().build());
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            e.accept(MILESTONE_ITEM.get());
            e.accept(INN_SIGN_ITEM.get());
        }
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(ATLAS.get());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
