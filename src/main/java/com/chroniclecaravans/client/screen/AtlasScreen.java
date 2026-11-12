package com.chroniclecaravans.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.chroniclecaravans.menu.AtlasMenu;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.platform.Nbt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The caravan atlas: a parchment map of the stops, inns and routes (and caravans, for cartographers).
 * Click a place or a route to see it, and rename it if it is yours. Drag to move, scroll to zoom.
 */
public class AtlasScreen extends CCScreen implements MenuAccess<AtlasMenu> {
    private static final int W = 380, H = 236, MAP_W = 244, MAP_H = 190;
    private static final int ROAD = 0xFF6A4520, LINK = 0xB09A7A4A;

    private record Place(BlockPos pos, int kind, boolean mine, ItemStack stack, String owner) {}

    private record Route(BlockPos a, BlockPos b, String name, boolean built, boolean mine) {}

    private record Car(int x, int z, boolean hasTarget, int tx, int tz, int color, Component name) {}

    private final AtlasMenu menu;
    private int x0, y0;
    private double cx, cz;
    private double scale = 0.45;
    private boolean centred;
    private boolean dragging;
    private Place selPlace;
    private Route selRoute;
    private StringBuilder editing;
    private int renameY;

    public AtlasScreen(AtlasMenu menu, Inventory inv, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    public AtlasMenu getMenu() {
        return menu;
    }

    @Override
    protected void init() {
        super.init();
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        if (!centred && minecraft.player != null) {
            cx = minecraft.player.getX();
            cz = minecraft.player.getZ();
            centred = true;
        }
    }

    @Override
    public void onClose() {
        if (minecraft.player != null) minecraft.player.closeContainer();
        else super.onClose();
    }

    @Override
    public void removed() {
        if (minecraft.player != null) menu.removed(minecraft.player);
        super.removed();
    }

    // ---------------------------------------------------------------- content

    private List<Place> places() {
        List<Place> out = new ArrayList<>();
        for (int i = 0; i < menu.count(AtlasMenu.D_STOPS); i++) {
            ItemStack st = menu.stop(i);
            CompoundTag t = Compat.data(st);
            if (t == null) continue;
            out.add(new Place(new BlockPos(Nbt.getInt(t, "X"), Nbt.getInt(t, "Y"), Nbt.getInt(t, "Z")), Nbt.getInt(t, "Kind"),
                    Nbt.getBoolean(t, "Mine"), st, Nbt.getString(t, "Owner")));
        }
        return out;
    }

    private List<Route> routes() {
        List<Route> out = new ArrayList<>();
        for (int i = 0; i < menu.count(AtlasMenu.D_ROUTES); i++) {
            CompoundTag t = Compat.data(menu.route(i));
            if (t == null) continue;
            out.add(new Route(BlockPos.of(Nbt.getLong(t, "A")), BlockPos.of(Nbt.getLong(t, "B")), Nbt.getString(t, "Name"),
                    Nbt.getBoolean(t, "Built"), Nbt.getBoolean(t, "Mine")));
        }
        return out;
    }

    private List<Car> cars() {
        List<Car> out = new ArrayList<>();
        for (int i = 0; i < menu.count(AtlasMenu.D_CARAVANS); i++) {
            ItemStack st = menu.caravan(i);
            CompoundTag t = Compat.data(st);
            if (t == null) continue;
            out.add(new Car(Nbt.getInt(t, "X"), Nbt.getInt(t, "Z"), Nbt.getBoolean(t, "HasTarget"), Nbt.getInt(t, "TX"), Nbt.getInt(t, "TZ"),
                    0xFF000000 | Nbt.getInt(t, "Color"), st.getHoverName()));
        }
        return out;
    }

    private Place placeAt(BlockPos pos) {
        for (Place p : places()) if (p.pos.equals(pos)) return p;
        return null;
    }

    private Component nameOf(BlockPos pos) {
        Place p = placeAt(pos);
        return p == null ? Component.literal(pos.getX() + ", " + pos.getZ()) : p.stack.getHoverName();
    }

    private static int pinOf(int kind) {
        return kind == AtlasMenu.KIND_VILLAGE ? UiKit.PIN_BLUE : kind == AtlasMenu.KIND_INN ? UiKit.PIN_GOLD : UiKit.PIN_RED;
    }

    // ---------------------------------------------------------------- map geometry

    private int mapX() {
        return x0 + 16;
    }

    private int mapY() {
        return y0 + 30;
    }

    private double sx(double wx) {
        return mapX() + MAP_W / 2.0 + (wx - cx) * scale;
    }

    private double sy(double wz) {
        return mapY() + MAP_H / 2.0 + (wz - cz) * scale;
    }

    private void line(GuiGraphics g, double x1, double y1, double x2, double y2, int color, int thick, boolean dashed) {
        double dx = x2 - x1, dy = y2 - y1;
        int steps = (int) Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)));
        if (steps > 4000) return;
        int mx0 = mapX(), my0 = mapY();
        for (int i = 0; i <= steps; i++) {
            if (dashed && (i / 3) % 2 == 1) continue;
            int px = (int) Math.round(x1 + dx * i / steps), py = (int) Math.round(y1 + dy * i / steps);
            if (px < mx0 - 2 || py < my0 - 2 || px > mx0 + MAP_W + 2 || py > my0 + MAP_H + 2) continue;
            g.fill(px, py, px + thick, py + thick, color);
        }
    }

    private static double segDist(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double l2 = dx * dx + dy * dy;
        double t = l2 < 1e-6 ? 0 : Mth.clamp(((px - ax) * dx + (py - ay) * dy) / l2, 0, 1);
        double qx = ax + dx * t, qy = ay + dy * t;
        return Math.sqrt((px - qx) * (px - qx) + (py - qy) * (py - qy));
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void draw(GuiGraphics g, int mx, int my, float pt) {
        UiKit.panel(g, x0, y0, W, H);
        UiKit.text(g, font, title.copy().withStyle(ChatFormatting.BOLD), x0 + 16, y0 + 15, UiKit.HEAD);
        int mx0 = mapX(), my0 = mapY();
        UiKit.inset(g, mx0 - 3, my0 - 3, MAP_W + 6, MAP_H + 6);
        g.enableScissor(mx0, my0, mx0 + MAP_W, my0 + MAP_H);
        UiKit.paper(g, mx0, my0, MAP_W, MAP_H);
        g.fill(mx0, my0, mx0 + MAP_W, my0 + MAP_H, 0x14603A10);
        // Grid every 64 blocks (or more when zoomed out)
        int step = 64;
        while (step * scale < 16) step *= 2;
        double wx0 = cx - MAP_W / 2.0 / scale, wz0 = cz - MAP_H / 2.0 / scale;
        for (int gx = (int) Math.floor(wx0 / step) * step; sx(gx) < mx0 + MAP_W; gx += step) {
            int px = (int) sx(gx);
            g.fill(px, my0, px + 1, my0 + MAP_H, 0x1C3B2A1A);
        }
        for (int gz = (int) Math.floor(wz0 / step) * step; sy(gz) < my0 + MAP_H; gz += step) {
            int py = (int) sy(gz);
            g.fill(mx0, py, mx0 + MAP_W, py + 1, 0x1C3B2A1A);
        }
        List<Place> places = places();
        List<Route> routes = routes();
        Route hoverRoute = null;
        Place hoverPlace = null;
        boolean inMap = in(mx, my, mx0, my0, MAP_W, MAP_H);
        if (inMap && editing == null) {
            hoverPlace = pickPlace(places, mx, my);
            if (hoverPlace == null) hoverRoute = pickRoute(routes, mx, my);
        }
        for (Route r : routes) {
            boolean sel = r.equals(selRoute), hov = r.equals(hoverRoute);
            int color = sel ? 0xFFB02818 : hov ? 0xFF2A6AB0 : r.built ? ROAD : LINK;
            line(g, sx(r.a.getX() + 0.5), sy(r.a.getZ() + 0.5), sx(r.b.getX() + 0.5), sy(r.b.getZ() + 0.5), color, r.built || sel ? 2 : 1, !r.built);
            if (!r.name.isEmpty() && scale >= 0.25) {
                double mxw = (r.a.getX() + r.b.getX()) / 2.0 + 0.5, mzw = (r.a.getZ() + r.b.getZ()) / 2.0 + 0.5;
                String n = UiKit.fit(font, r.name, 90);
                int tx = (int) sx(mxw) - font.width(n) / 2, ty = (int) sy(mzw) - 4;
                g.fill(tx - 2, ty - 1, tx + font.width(n) + 1, ty + 9, 0xC0E9D9B0);
                UiKit.text(g, font, n, tx, ty, 0xFF6A2A10);
            }
        }
        // Caravans and where they are going
        if (menu.cartographer()) {
            for (Car c : cars()) {
                double px = sx(c.x + 0.5), py = sy(c.z + 0.5);
                if (c.hasTarget) line(g, px, py, sx(c.tx + 0.5), sy(c.tz + 0.5), c.color, 1, true);
                g.fill((int) px - 3, (int) py - 3, (int) px + 3, (int) py + 3, 0xFF2A1C10);
                g.fill((int) px - 2, (int) py - 2, (int) px + 2, (int) py + 2, c.color);
            }
        }
        // Pins, then the names where there is room (the selected and hovered places first)
        for (Place p : places) {
            int px = (int) sx(p.pos.getX() + 0.5), py = (int) sy(p.pos.getZ() + 0.5);
            boolean sel = p.equals(selPlace), hov = p.equals(hoverPlace);
            if (sel || hov) g.fill(px - 6, py - 15, px + 6, py + 2, sel ? 0x50B02818 : 0x402A6AB0);
            UiKit.pin(g, px, py, sel ? UiKit.PIN_GREEN : pinOf(p.kind));
        }
        List<int[]> taken = new ArrayList<>();
        List<Place> order = new ArrayList<>(places);
        final Place hovered = hoverPlace;
        order.sort((a, b) -> Boolean.compare(!(a.equals(selPlace) || a.equals(hovered)), !(b.equals(selPlace) || b.equals(hovered))));
        for (Place p : order) {
            boolean sel = p.equals(selPlace), hov = p.equals(hovered);
            if (scale < 0.3 && !sel && !hov) continue;
            int px = (int) sx(p.pos.getX() + 0.5), py = (int) sy(p.pos.getZ() + 0.5);
            String n = UiKit.fit(font, p.stack.getHoverName().getString(), 100);
            int tx = px - font.width(n) / 2;
            int[] r = {tx - 2, py + 2, tx + font.width(n) + 1, py + 12};
            boolean clash = false;
            for (int[] o : taken) if (r[0] < o[2] && o[0] < r[2] && r[1] < o[3] && o[1] < r[3]) clash = true;
            if (clash && !sel && !hov) continue;
            taken.add(r);
            g.fill(r[0], r[1], r[2], r[3], 0xC8EADBB4);
            UiKit.text(g, font, n, tx, py + 3, UiKit.INK);
        }
        // The player
        if (minecraft.player != null) {
            int px = (int) sx(minecraft.player.getX()), py = (int) sy(minecraft.player.getZ());
            g.fill(px - 3, py - 3, px + 3, py + 3, 0xFFFFFFFF);
            g.fill(px - 2, py - 2, px + 2, py + 2, 0xFFD02020);
        }
        g.disableScissor();
        UiKit.compass(g, mx0 + MAP_W - 50, my0 + 2);
        // Scale bar
        int bar = 64;
        while (bar * scale > 70) bar /= 2;
        while (bar * scale < 25) bar *= 2;
        int bl = (int) (bar * scale);
        g.fill(mx0 + 6, my0 + MAP_H - 8, mx0 + 6 + bl, my0 + MAP_H - 6, UiKit.INK);
        g.fill(mx0 + 6, my0 + MAP_H - 11, mx0 + 7, my0 + MAP_H - 6, UiKit.INK);
        g.fill(mx0 + 5 + bl, my0 + MAP_H - 11, mx0 + 6 + bl, my0 + MAP_H - 6, UiKit.INK);
        UiKit.text(g, font, bar + " m", mx0 + 10 + bl, my0 + MAP_H - 12, UiKit.INK);

        drawSide(g, mx, my);
        if (hoverPlace != null && !hoverPlace.equals(selPlace)) {
            tooltip(g, List.of(hoverPlace.stack.getHoverName(), Component.literal(hoverPlace.pos.getX() + ", " + hoverPlace.pos.getZ()).withStyle(ChatFormatting.GRAY)), mx, my);
        } else if (hoverRoute != null && !hoverRoute.equals(selRoute)) {
            tooltip(g, List.of(routeTitle(hoverRoute), Component.translatable("gui.chroniclecaravans.atlas.between", nameOf(hoverRoute.a), nameOf(hoverRoute.b)).withStyle(ChatFormatting.GRAY)), mx, my);
        }
    }

    private Component routeTitle(Route r) {
        return r.name.isEmpty() ? Component.translatable("gui.chroniclecaravans.atlas.unnamed").withStyle(ChatFormatting.ITALIC)
                : Component.literal(r.name);
    }

    private int sideX() {
        return x0 + 16 + MAP_W + 9;
    }

    private int sideW() {
        return W - (16 + MAP_W + 9) - 14;
    }

    private void drawSide(GuiGraphics g, int mx, int my) {
        int x = sideX(), w = sideW(), y = mapY() - 3;
        UiKit.card(g, x, y, w, MAP_H + 6, UiKit.CARD);
        x += 6;
        w -= 12;
        y += 6;
        if (selPlace != null) {
            UiKit.pin(g, x + 4, y + 13, pinOf(selPlace.kind));
            y = UiKit.wrap(g, font, selPlace.stack.getHoverName().copy().withStyle(ChatFormatting.BOLD), x + 12, y + 2, w - 12, UiKit.HEAD) + 3;
            String kind = selPlace.kind == AtlasMenu.KIND_VILLAGE ? "village" : selPlace.kind == AtlasMenu.KIND_INN ? "inn" : "milestone";
            y = UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.atlas.kind." + kind), x, y, w, UiKit.INK) + 2;
            if (!selPlace.owner.isEmpty()) y = UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.atlas.owner", selPlace.owner), x, y, w, UiKit.INK_SOFT);
            y = UiKit.wrap(g, font, Component.literal(selPlace.pos.getX() + ", " + selPlace.pos.getY() + ", " + selPlace.pos.getZ()), x, y, w, UiKit.INK_SOFT);
            if (minecraft.player != null) {
                int d = (int) Math.sqrt(selPlace.pos.distToCenterSqr(minecraft.player.position()));
                y = UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.blocks", d), x, y, w, UiKit.INK_SOFT);
            }
            editor(g, x, y + 6, w, selPlace.mine, mx, my);
        } else if (selRoute != null) {
            UiKit.icon(g, UiKit.I_ROAD, x, y);
            y = UiKit.wrap(g, font, routeTitle(selRoute).copy().withStyle(ChatFormatting.BOLD), x + 13, y + 1, w - 13, UiKit.HEAD) + 3;
            y = UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.atlas.between", nameOf(selRoute.a), nameOf(selRoute.b)), x, y, w, UiKit.INK) + 2;
            int len = (int) Math.sqrt(selRoute.a.distSqr(selRoute.b));
            y = UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.blocks", len), x, y, w, UiKit.INK_SOFT);
            y = UiKit.wrap(g, font, Component.translatable(selRoute.built ? "gui.chroniclecaravans.atlas.built" : "gui.chroniclecaravans.atlas.link"), x, y, w,
                    selRoute.built ? UiKit.GOOD : UiKit.INK_SOFT);
            editor(g, x, y + 6, w, selRoute.mine, mx, my);
        } else {
            y = UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.atlas.legend").withStyle(ChatFormatting.BOLD), x, y, w, UiKit.HEAD) + 6;
            int[] pins = {UiKit.PIN_RED, UiKit.PIN_BLUE, UiKit.PIN_GOLD};
            String[] keys = {"milestone", "village", "inn"};
            for (int i = 0; i < 3; i++) {
                UiKit.pin(g, x + 5, y + 12, pins[i]);
                UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.atlas.kind." + keys[i]), x + 14, y + 3, UiKit.INK);
                y += 15;
            }
            g.fill(x + 1, y + 4, x + 11, y + 6, ROAD);
            UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.atlas.built"), x + 14, y + 1, UiKit.INK);
            y += 12;
            for (int i = 0; i < 11; i += 6) g.fill(x + 1 + i, y + 4, x + 4 + i, y + 5, LINK);
            UiKit.text(g, font, Component.translatable("gui.chroniclecaravans.atlas.link"), x + 14, y + 1, UiKit.INK);
            y += 16;
            if (!menu.cartographer()) y = UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.atlas.no_caravans"), x, y, w, UiKit.INK_SOFT) + 4;
            UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.atlas.help"), x, y, w, UiKit.INK_SOFT);
        }
    }

    private void editor(GuiGraphics g, int x, int y, int w, boolean mine, int mx, int my) {
        renameY = y;
        if (!mine) {
            UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.atlas.not_yours"), x, y, w, UiKit.INK_SOFT);
            return;
        }
        if (editing == null) {
            boolean hover = in(mx, my, x, y, w, 18);
            UiKit.button(g, font, x, y, w, 18, Component.translatable("gui.chroniclecaravans.atlas.rename"), hover ? UiKit.BUTTON_HOVER : UiKit.BUTTON);
            return;
        }
        UiKit.inset(g, x, y, w, 16);
        String shown = editing.toString();
        while (font.width(shown + "_") > w - 6 && !shown.isEmpty()) shown = shown.substring(1);
        boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
        UiKit.text(g, font, shown + (blink ? "_" : ""), x + 3, y + 4, 0xFFFFF4DC);
        UiKit.wrap(g, font, Component.translatable("gui.chroniclecaravans.atlas.edit_help"), x, y + 20, w, UiKit.INK_SOFT);
    }

    // ---------------------------------------------------------------- input

    private Place pickPlace(List<Place> places, double mx, double my) {
        Place best = null;
        double bestD = 9;
        for (Place p : places) {
            double px = sx(p.pos.getX() + 0.5), py = sy(p.pos.getZ() + 0.5) - 7;
            double d = Math.max(Math.abs(mx - px), Math.abs(my - py));
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private Route pickRoute(List<Route> routes, double mx, double my) {
        Route best = null;
        double bestD = 4;
        for (Route r : routes) {
            double d = segDist(mx, my, sx(r.a.getX() + 0.5), sy(r.a.getZ() + 0.5), sx(r.b.getX() + 0.5), sy(r.b.getZ() + 0.5));
            if (d < bestD) {
                bestD = d;
                best = r;
            }
        }
        return best;
    }

    private void commit() {
        if (editing == null || minecraft.player == null) return;
        String name = editing.toString().strip();
        String arg = name.isEmpty() ? "-" : name;
        if (selPlace != null) {
            BlockPos p = selPlace.pos;
            minecraft.player.connection.sendCommand("caravans rename stop " + p.getX() + " " + p.getY() + " " + p.getZ() + " " + arg);
        } else if (selRoute != null) {
            BlockPos a = selRoute.a, b = selRoute.b;
            minecraft.player.connection.sendCommand("caravans rename route " + a.getX() + " " + a.getY() + " " + a.getZ() + " "
                    + b.getX() + " " + b.getY() + " " + b.getZ() + " " + arg);
        }
        editing = null;
        selPlace = null;
        selRoute = null;
        ding();
    }

    @Override
    protected boolean click(double mx, double my, int button, boolean shift) {
        int x = sideX() + 6, w = sideW() - 12;
        if ((selPlace != null && selPlace.mine) || (selRoute != null && selRoute.mine)) {
            if (editing == null && in(mx, my, x, renameY, w, 18)) {
                ding();
                if (selPlace != null) {
                    String n = selPlace.stack.getHoverName().getString();
                    editing = new StringBuilder(selPlace.kind == AtlasMenu.KIND_VILLAGE && n.contains("(") ? "" : n);
                } else {
                    editing = new StringBuilder(selRoute.name);
                }
                return true;
            }
        }
        if (in(mx, my, mapX(), mapY(), MAP_W, MAP_H)) {
            Place p = pickPlace(places(), mx, my);
            Route r = p == null ? pickRoute(routes(), mx, my) : null;
            editing = null;
            if (p != null || r != null) {
                selPlace = p;
                selRoute = r;
                page();
            } else {
                selPlace = null;
                selRoute = null;
                dragging = true;
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean drag(double mx, double my, double dx, double dy) {
        if (!dragging) return false;
        cx -= dx / scale;
        cz -= dy / scale;
        return true;
    }

    @Override
    protected void release() {
        dragging = false;
    }

    @Override
    protected boolean scroll(double mx, double my, double amount) {
        scale = Mth.clamp(scale * (amount > 0 ? 1.25 : 0.8), 0.04, 4.0);
        return true;
    }

    @Override
    protected boolean key(int key) {
        if (editing == null) return false;
        switch (key) {
            case 256 -> editing = null;                       // escape: cancel
            case 257, 335 -> commit();                         // enter
            case 259 -> {                                     // backspace
                if (editing.length() > 0) editing.setLength(editing.length() - 1);
            }
            default -> {
                return true;
            }
        }
        return true;
    }

    @Override
    protected boolean typed(char c) {
        if (editing == null) return false;
        if (c >= ' ' && c != '§' && c != 127 && editing.length() < 32) editing.append(c);
        return true;
    }

    public void select(int placeIndex) {
        List<Place> places = places();
        if (placeIndex < places.size()) {
            selPlace = places.get(placeIndex);
            cx = selPlace.pos.getX();
            cz = selPlace.pos.getZ();
        }
    }
}
