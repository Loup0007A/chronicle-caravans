package com.chroniclecaravans.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.menu.AtlasMenu;
import com.chroniclecaravans.platform.Compat;
import com.chroniclecaravans.platform.Nbt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The caravan atlas: a parchment map of the stops, inns and routes (and caravans, for cartographers).
 * Click a place or a route to see it, and rename it if it is yours. Drag to move, scroll to zoom.
 */
public class AtlasScreen extends CCScreen implements MenuAccess<AtlasMenu> {
    private static final int W = 372, H = 236, MAP_W = 248, MAP_H = 208, SIDE = MAP_W + 12;
    private static final int INK = 0xFF3A2A14, PARCHMENT = 0xFFE9D9B0, ROAD = 0xFF6A4520, LINK = 0xFF9A7A4A;

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

    // ---------------------------------------------------------------- map geometry

    private int mapX() {
        return x0 + 6;
    }

    private int mapY() {
        return y0 + 22;
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
        frame(g, x0 - 2, y0 - 2, W + 4, H + 4, DARK, BROWN);
        frame(g, x0, y0, W, H, PANEL, GOLD);
        text(g, title.copy().withStyle(ChatFormatting.BOLD), x0 + 8, y0 + 7, GOLD);
        int mx0 = mapX(), my0 = mapY();
        frame(g, mx0 - 1, my0 - 1, MAP_W + 2, MAP_H + 2, PARCHMENT, BROWN);
        g.enableScissor(mx0, my0, mx0 + MAP_W, my0 + MAP_H);
        // Grid every 64 blocks (or more when zoomed out)
        int step = 64;
        while (step * scale < 14) step *= 2;
        double wx0 = cx - MAP_W / 2.0 / scale, wz0 = cz - MAP_H / 2.0 / scale;
        for (int gx = (int) Math.floor(wx0 / step) * step; sx(gx) < mx0 + MAP_W; gx += step) {
            int px = (int) sx(gx);
            g.fill(px, my0, px + 1, my0 + MAP_H, 0x22000000);
        }
        for (int gz = (int) Math.floor(wz0 / step) * step; sy(gz) < my0 + MAP_H; gz += step) {
            int py = (int) sy(gz);
            g.fill(mx0, py, mx0 + MAP_W, py + 1, 0x22000000);
        }
        List<Place> places = places();
        List<Route> routes = routes();
        Route hoverRoute = null;
        Place hoverPlace = null;
        boolean inMap = in(mx, my, mx0, my0, MAP_W, MAP_H);
        if (inMap) {
            hoverPlace = pickPlace(places, mx, my);
            if (hoverPlace == null) hoverRoute = pickRoute(routes, mx, my);
        }
        for (Route r : routes) {
            boolean sel = r.equals(selRoute), hov = r.equals(hoverRoute);
            int color = sel ? 0xFFB02818 : hov ? 0xFF2A6AB0 : r.built ? ROAD : LINK;
            line(g, sx(r.a.getX() + 0.5), sy(r.a.getZ() + 0.5), sx(r.b.getX() + 0.5), sy(r.b.getZ() + 0.5), color, r.built || sel ? 2 : 1, !r.built);
            if (!r.name.isEmpty() && scale >= 0.25) {
                double mxw = (r.a.getX() + r.b.getX()) / 2.0 + 0.5, mzw = (r.a.getZ() + r.b.getZ()) / 2.0 + 0.5;
                String n = fit(r.name, 90);
                int tx = (int) sx(mxw) - font.width(n) / 2, ty = (int) sy(mzw) - 4;
                g.fill(tx - 2, ty - 1, tx + font.width(n) + 1, ty + 9, 0xC0E9D9B0);
                text(g, n, tx, ty, 0xFF6A2A10);
            }
        }
        // Caravans and where they are going
        if (menu.cartographer()) {
            for (Car c : cars()) {
                double px = sx(c.x + 0.5), py = sy(c.z + 0.5);
                if (c.hasTarget) line(g, px, py, sx(c.tx + 0.5), sy(c.tz + 0.5), c.color, 1, true);
                g.fill((int) px - 3, (int) py - 3, (int) px + 3, (int) py + 3, 0xFF000000);
                g.fill((int) px - 2, (int) py - 2, (int) px + 2, (int) py + 2, c.color);
            }
        }
        for (Place p : places) {
            int px = (int) sx(p.pos.getX() + 0.5), py = (int) sy(p.pos.getZ() + 0.5);
            boolean sel = p.equals(selPlace), hov = p.equals(hoverPlace);
            if (sel || hov) g.fill(px - 10, py - 10, px + 10, py + 10, sel ? 0x60B02818 : 0x402A6AB0);
            g.renderItem(p.stack, px - 8, py - 8);
        }
        // Names under the icons: the selected and hovered places first, then the others where there is room
        List<int[]> taken = new ArrayList<>();
        List<Place> order = new ArrayList<>(places);
        final Place hovered = hoverPlace;
        order.sort((a, b) -> Boolean.compare(!(a.equals(selPlace) || a.equals(hovered)), !(b.equals(selPlace) || b.equals(hovered))));
        for (Place p : order) {
            boolean sel = p.equals(selPlace), hov = p.equals(hovered);
            if (scale < 0.3 && !sel && !hov) continue;
            int px = (int) sx(p.pos.getX() + 0.5), py = (int) sy(p.pos.getZ() + 0.5);
            String n = fit(p.stack.getHoverName().getString(), 100);
            int tx = px - font.width(n) / 2;
            int[] r = {tx - 2, py + 8, tx + font.width(n) + 1, py + 18};
            boolean clash = false;
            for (int[] o : taken) if (r[0] < o[2] && o[0] < r[2] && r[1] < o[3] && o[1] < r[3]) clash = true;
            if (clash && !sel && !hov) continue;
            taken.add(r);
            g.fill(r[0], r[1], r[2], r[3], 0xC0E9D9B0);
            text(g, n, tx, py + 9, INK);
        }
        // The player
        if (minecraft.player != null) {
            int px = (int) sx(minecraft.player.getX()), py = (int) sy(minecraft.player.getZ());
            g.fill(px - 3, py - 3, px + 3, py + 3, 0xFFFFFFFF);
            g.fill(px - 2, py - 2, px + 2, py + 2, 0xFFD02020);
        }
        g.disableScissor();
        // Scale bar
        int bar = 64;
        while (bar * scale > 70) bar /= 2;
        while (bar * scale < 25) bar *= 2;
        int bl = (int) (bar * scale);
        g.fill(mx0 + 6, my0 + MAP_H - 8, mx0 + 6 + bl, my0 + MAP_H - 6, INK);
        text(g, bar + " m", mx0 + 10 + bl, my0 + MAP_H - 12, INK);

        drawSide(g, mx, my);
        if (hoverPlace != null && !hoverPlace.equals(selPlace)) {
            tooltip(g, List.of(hoverPlace.stack.getHoverName(), Component.literal(hoverPlace.pos.getX() + ", " + hoverPlace.pos.getZ()).withStyle(ChatFormatting.GRAY)), mx, my);
        } else if (hoverRoute != null && !hoverRoute.equals(selRoute)) {
            tooltip(g, List.of(routeTitle(hoverRoute), Component.translatable("gui.chroniclecaravans.atlas.between", nameOf(hoverRoute.a), nameOf(hoverRoute.b)).withStyle(ChatFormatting.GRAY)), mx, my);
        }
    }

    private Component routeTitle(Route r) {
        return r.name.isEmpty() ? Component.translatable("gui.chroniclecaravans.atlas.unnamed").withStyle(ChatFormatting.ITALIC)
                : Component.literal(r.name).withStyle(ChatFormatting.GOLD);
    }

    private int wrap(GuiGraphics g, Component c, int x, int y, int w, int color) {
        for (FormattedCharSequence line : font.split(c, w)) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private int sideX() {
        return x0 + SIDE;
    }

    private int sideW() {
        return W - SIDE - 8;
    }

    private void drawSide(GuiGraphics g, int mx, int my) {
        int x = sideX(), w = sideW(), y = y0 + 22;
        frame(g, x, y, w, MAP_H, 0xFF33271A, BROWN);
        x += 5;
        w -= 10;
        y += 6;
        if (selPlace != null) {
            y = wrap(g, selPlace.stack.getHoverName().copy().withStyle(ChatFormatting.BOLD), x, y, w, GOLD) + 2;
            String kind = selPlace.kind == AtlasMenu.KIND_VILLAGE ? "village" : selPlace.kind == AtlasMenu.KIND_INN ? "inn" : "milestone";
            y = wrap(g, Component.translatable("gui.chroniclecaravans.atlas.kind." + kind), x, y, w, TEXT) + 2;
            if (!selPlace.owner.isEmpty()) y = wrap(g, Component.translatable("gui.chroniclecaravans.atlas.owner", selPlace.owner), x, y, w, MUTED);
            y = wrap(g, Component.literal(selPlace.pos.getX() + ", " + selPlace.pos.getY() + ", " + selPlace.pos.getZ()), x, y, w, MUTED);
            if (minecraft.player != null) {
                int d = (int) Math.sqrt(selPlace.pos.distToCenterSqr(minecraft.player.position()));
                y = wrap(g, Component.translatable("gui.chroniclecaravans.blocks", d), x, y, w, MUTED);
            }
            editor(g, x, y + 6, w, selPlace.mine, mx, my);
        } else if (selRoute != null) {
            y = wrap(g, routeTitle(selRoute).copy().withStyle(ChatFormatting.BOLD), x, y, w, GOLD) + 2;
            y = wrap(g, Component.translatable("gui.chroniclecaravans.atlas.between", nameOf(selRoute.a), nameOf(selRoute.b)), x, y, w, TEXT) + 2;
            int len = (int) Math.sqrt(selRoute.a.distSqr(selRoute.b));
            y = wrap(g, Component.translatable("gui.chroniclecaravans.blocks", len), x, y, w, MUTED);
            y = wrap(g, Component.translatable(selRoute.built ? "gui.chroniclecaravans.atlas.built" : "gui.chroniclecaravans.atlas.link"), x, y, w,
                    selRoute.built ? GREEN : MUTED);
            editor(g, x, y + 6, w, selRoute.mine, mx, my);
        } else {
            y = wrap(g, Component.translatable("gui.chroniclecaravans.atlas.legend").withStyle(ChatFormatting.BOLD), x, y, w, GOLD) + 4;
            ItemStack[] icons = {new ItemStack(ChronicleCaravans.MILESTONE_ITEM.get()), new ItemStack(Items.BELL), new ItemStack(ChronicleCaravans.INN_SIGN_ITEM.get())};
            String[] keys = {"milestone", "village", "inn"};
            for (int i = 0; i < 3; i++) {
                g.renderItem(icons[i], x, y);
                text(g, Component.translatable("gui.chroniclecaravans.atlas.kind." + keys[i]), x + 19, y + 4, TEXT);
                y += 18;
            }
            g.fill(x + 1, y + 4, x + 15, y + 6, ROAD);
            text(g, Component.translatable("gui.chroniclecaravans.atlas.built"), x + 19, y + 1, TEXT);
            y += 12;
            for (int i = 0; i < 15; i += 6) g.fill(x + 1 + i, y + 4, x + 4 + i, y + 5, LINK);
            text(g, Component.translatable("gui.chroniclecaravans.atlas.link"), x + 19, y + 1, TEXT);
            y += 16;
            if (!menu.cartographer()) y = wrap(g, Component.translatable("gui.chroniclecaravans.atlas.no_caravans"), x, y, w, MUTED) + 4;
            wrap(g, Component.translatable("gui.chroniclecaravans.atlas.help"), x, y, w, MUTED);
        }
    }

    private int renameY;

    private void editor(GuiGraphics g, int x, int y, int w, boolean mine, int mx, int my) {
        renameY = y;
        if (!mine) {
            wrap(g, Component.translatable("gui.chroniclecaravans.atlas.not_yours"), x, y, w, MUTED);
            return;
        }
        if (editing == null) {
            boolean hover = in(mx, my, x, y, w, 16);
            frame(g, x, y, w, 16, hover ? 0xFF6B4A22 : 0xFF4E361A, GOLD);
            Component l = Component.translatable("gui.chroniclecaravans.atlas.rename");
            text(g, l, x + (w - font.width(l)) / 2, y + 4, TEXT);
            return;
        }
        frame(g, x, y, w, 16, 0xFF120E0A, GOLD);
        String shown = editing.toString();
        while (font.width(shown + "_") > w - 6 && !shown.isEmpty()) shown = shown.substring(1);
        boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
        text(g, shown + (blink ? "_" : ""), x + 3, y + 4, 0xFFFFFFFF);
        wrap(g, Component.translatable("gui.chroniclecaravans.atlas.edit_help"), x, y + 20, w, MUTED);
    }

    // ---------------------------------------------------------------- input

    private Place pickPlace(List<Place> places, double mx, double my) {
        Place best = null;
        double bestD = 9;
        for (Place p : places) {
            double d = Math.max(Math.abs(mx - sx(p.pos.getX() + 0.5)), Math.abs(my - sy(p.pos.getZ() + 0.5)));
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

    private void startEditing(String current) {
        editing = new StringBuilder(current);
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
        int x = sideX() + 5, w = sideW() - 10;
        if ((selPlace != null && selPlace.mine) || (selRoute != null && selRoute.mine)) {
            if (editing == null && in(mx, my, x, renameY, w, 16)) {
                ding();
                if (selPlace != null) {
                    String n = selPlace.stack.getHoverName().getString();
                    startEditing(selPlace.kind == AtlasMenu.KIND_VILLAGE && n.contains("(") ? "" : n);
                } else {
                    startEditing(selRoute.name);
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
                ding();
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
