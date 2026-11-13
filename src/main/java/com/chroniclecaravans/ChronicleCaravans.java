package com.chroniclecaravans;

import com.chroniclecaravans.block.DockBlock;
import com.chroniclecaravans.block.DockBlockEntity;
import com.chroniclecaravans.block.HitchingPostBlock;
import com.chroniclecaravans.block.InnSignBlock;
import com.chroniclecaravans.block.TradingPostBlock;
import com.chroniclecaravans.block.TradingPostBlockEntity;
import com.chroniclecaravans.item.ParcelItem;
import com.chroniclecaravans.menu.DockMenu;
import com.chroniclecaravans.menu.ParcelMenu;
import com.chroniclecaravans.menu.TradingPostMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
import net.minecraft.world.item.Item;
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

    private static BlockBehaviour.Properties crateProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5F)
                .sound(SoundType.WOOD)
                .ignitedByLava();
    }

    private static BlockBehaviour.Properties stallProps() {
        return crateProps().noOcclusion();
    }

    //#if mc26
    //$$ private static <T extends BlockEntity> BlockEntityType<T> beType(BlockEntityType.BlockEntitySupplier<T> f, Block block) {
    //$$     return new BlockEntityType<>(f, block);
    //$$ }
    //#else
    @SuppressWarnings("DataFlowIssue")
    private static <T extends BlockEntity> BlockEntityType<T> beType(BlockEntityType.BlockEntitySupplier<T> f, Block block) {
        return BlockEntityType.Builder.of(f, block).build(null);
    }
    //#endif

    //#if mc26
    //$$ private static final java.util.function.Supplier<BlockBehaviour.Properties> PROPS = ChronicleCaravans::milestoneProps;
    //$$ private static final java.util.function.Supplier<BlockBehaviour.Properties> INN_PROPS = ChronicleCaravans::innProps;
    //$$ private static final java.util.function.Supplier<BlockBehaviour.Properties> CRATE_PROPS = ChronicleCaravans::crateProps;
    //$$ private static final java.util.function.Supplier<BlockBehaviour.Properties> STALL_PROPS = ChronicleCaravans::stallProps;
    //$$ private static final java.util.function.UnaryOperator<net.minecraft.world.item.Item.Properties> SINGLE = p -> p.stacksTo(1);
    //#else
    private static final BlockBehaviour.Properties PROPS = milestoneProps();
    private static final BlockBehaviour.Properties INN_PROPS = innProps();
    private static final BlockBehaviour.Properties CRATE_PROPS = crateProps();
    private static final BlockBehaviour.Properties STALL_PROPS = stallProps();
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
    public static final DeferredBlock<DockBlock> DOCK = BLOCKS.registerBlock("loading_dock", DockBlock::new, CRATE_PROPS);
    public static final DeferredItem<BlockItem> DOCK_ITEM = ITEMS.registerSimpleBlockItem("loading_dock", DOCK);
    public static final DeferredBlock<TradingPostBlock> POST = BLOCKS.registerBlock("trading_post", TradingPostBlock::new, STALL_PROPS);
    public static final DeferredItem<BlockItem> POST_ITEM = ITEMS.registerSimpleBlockItem("trading_post", POST);
    public static final DeferredItem<ParcelItem> PARCEL = ITEMS.registerItem("caravan_parcel", ParcelItem::new, SINGLE);
    public static final DeferredBlock<HitchingPostBlock> HITCHING_POST = BLOCKS.registerBlock("hitching_post", HitchingPostBlock::new, INN_PROPS);
    public static final DeferredItem<BlockItem> HITCHING_POST_ITEM = ITEMS.registerSimpleBlockItem("hitching_post", HITCHING_POST);
    public static final DeferredItem<Item> CHARTER = ITEMS.registerSimpleItem("relay_charter");
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DockBlockEntity>> DOCK_BE = BLOCK_ENTITIES.register("loading_dock",
            () -> beType(DockBlockEntity::new, DOCK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TradingPostBlockEntity>> POST_BE = BLOCK_ENTITIES.register("trading_post",
            () -> beType(TradingPostBlockEntity::new, POST.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<DockMenu>> DOCK_MENU = MENUS.register("loading_dock",
            () -> new MenuType<>(DockMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<TradingPostMenu>> POST_MENU = MENUS.register("trading_post",
            () -> new MenuType<>(TradingPostMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<ParcelMenu>> PARCEL_MENU = MENUS.register("parcel",
            () -> new MenuType<>(ParcelMenu::new, FeatureFlags.DEFAULT_FLAGS));
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
    //$$ public static final RegistryObject<DockBlock> DOCK = BLOCKS.register("loading_dock", () -> new DockBlock(crateProps()));
    //$$ public static final RegistryObject<BlockItem> DOCK_ITEM = ITEMS.register("loading_dock", () -> new BlockItem(DOCK.get(), new Item.Properties()));
    //$$ public static final RegistryObject<TradingPostBlock> POST = BLOCKS.register("trading_post", () -> new TradingPostBlock(stallProps()));
    //$$ public static final RegistryObject<BlockItem> POST_ITEM = ITEMS.register("trading_post", () -> new BlockItem(POST.get(), new Item.Properties()));
    //$$ public static final RegistryObject<ParcelItem> PARCEL = ITEMS.register("caravan_parcel", () -> new ParcelItem(new Item.Properties().stacksTo(1)));
    //$$ public static final RegistryObject<HitchingPostBlock> HITCHING_POST = BLOCKS.register("hitching_post", () -> new HitchingPostBlock(innProps()));
    //$$ public static final RegistryObject<BlockItem> HITCHING_POST_ITEM = ITEMS.register("hitching_post", () -> new BlockItem(HITCHING_POST.get(), new Item.Properties()));
    //$$ public static final RegistryObject<Item> CHARTER = ITEMS.register("relay_charter", () -> new Item(new Item.Properties()));
    //$$ public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    //$$ public static final RegistryObject<BlockEntityType<DockBlockEntity>> DOCK_BE = BLOCK_ENTITIES.register("loading_dock",
    //$$         () -> beType(DockBlockEntity::new, DOCK.get()));
    //$$ public static final RegistryObject<BlockEntityType<TradingPostBlockEntity>> POST_BE = BLOCK_ENTITIES.register("trading_post",
    //$$         () -> beType(TradingPostBlockEntity::new, POST.get()));
    //$$ public static final RegistryObject<MenuType<DockMenu>> DOCK_MENU = MENUS.register("loading_dock",
    //$$         () -> new MenuType<>(DockMenu::new, FeatureFlags.DEFAULT_FLAGS));
    //$$ public static final RegistryObject<MenuType<TradingPostMenu>> POST_MENU = MENUS.register("trading_post",
    //$$         () -> new MenuType<>(TradingPostMenu::new, FeatureFlags.DEFAULT_FLAGS));
    //$$ public static final RegistryObject<MenuType<ParcelMenu>> PARCEL_MENU = MENUS.register("parcel",
    //$$         () -> new MenuType<>(ParcelMenu::new, FeatureFlags.DEFAULT_FLAGS));
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
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(this::attributes);
        modBus.addListener(this::creativeTab);
        //#if neoforge
        modBus.addListener(this::capabilities);
        //#endif
    }

    //#if neoforge
    /** Pipes and funnels of other mods see the docks and trading posts like hoppers do (same faces, same rules). */
    private void capabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e) {
        //#if mc26
        //$$ e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, DOCK_BE.get(),
        //$$         (be, side) -> new net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper(be, side == null ? net.minecraft.core.Direction.UP : side));
        //$$ e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, POST_BE.get(),
        //$$         (be, side) -> new net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper(be, side == null ? net.minecraft.core.Direction.UP : side));
        //#else
        e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, DOCK_BE.get(),
                (be, side) -> new net.neoforged.neoforge.items.wrapper.SidedInvWrapper(be, side == null ? net.minecraft.core.Direction.UP : side));
        e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, POST_BE.get(),
                (be, side) -> new net.neoforged.neoforge.items.wrapper.SidedInvWrapper(be, side == null ? net.minecraft.core.Direction.UP : side));
        //#endif
    }
    //#endif

    private void attributes(EntityAttributeCreationEvent e) {
        e.put(CARAVAN_MASTER.get(), CaravanMaster.createAttributes().build());
        e.put(GUARD.get(), CaravanGuard.createAttributes().build());
        e.put(BUILDER.get(), RoadBuilder.createAttributes().build());
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            e.accept(MILESTONE_ITEM.get());
            e.accept(INN_SIGN_ITEM.get());
            e.accept(DOCK_ITEM.get());
            e.accept(POST_ITEM.get());
            e.accept(HITCHING_POST_ITEM.get());
        }
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(ATLAS.get());
            e.accept(CHARTER.get());
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
