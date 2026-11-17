package com.bdmajora.impetus.umbra.gui.modern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.widgets.AbstractWidget;
import com.bdmajora.impetus.engine.impl.gui.widgets.FlatButtonWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.impetus.impl.gui.VintageDrawContext;
import com.bdmajora.impetus.impl.gui.VintageInteractionContext;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.config.UmbraConfig;

import java.awt.Desktop;
import java.util.ArrayList;
import java.util.List;

// Screen that lists shader packs to pick from.
public class ShaderPackSelectScreen extends GuiScreen {
    // Top of the pack list.
    private static final int LIST_TOP = 44;
    // Height of a row.
    private static final int ROW_HEIGHT = 22;
    // Width of a row.
    private static final int ROW_WIDTH = 420;

    // Screen to return to.
    private final GuiScreen parent;

    // Names of the installed packs.
    private List<String> packs = new ArrayList<>();
    // Pack picked but not applied.
    private String stagedSelection;
    // Shader switch picked but not applied.
    private boolean stagedEnabled;
    // Index of the shown page.
    private int page;

    // Widgets of the screen.
    private final List<AbstractWidget> widgets = new ArrayList<>();
    // Draw context of the screen.
    private final DrawContext drawContext = new VintageDrawContext();

    // Creates a shader pack select screen from its parent.
    public ShaderPackSelectScreen(GuiScreen parent) {
        this.parent = parent;
    }

    // Initializes the gui.
    @Override
    public void initGui() {
        this.widgets.clear();

        if (this.stagedSelection == null) {
            this.stagedEnabled = Umbra.areShadersEnabled();
            // Stages the remembered pack while shaders are off.
            this.stagedSelection = this.stagedEnabled ? Umbra.getSelectedPackName() : Umbra.getLastPackName();
        }

        this.packs = new ArrayList<>();
        this.packs.addAll(Umbra.listAvailablePacks());

        int rowsPerPage = Math.max(1, (this.height - LIST_TOP - 80) / ROW_HEIGHT) - 1;
        int totalPages = Math.max(1, (int) Math.ceil(this.packs.size() / (double) rowsPerPage));
        this.page = Math.max(0, Math.min(this.page, totalPages - 1));

        int rowLeft = (this.width - ROW_WIDTH) / 2;
        int y = LIST_TOP;

        addRow(rowLeft, y, "Shaders: " + (this.stagedEnabled ? "§aEnabled" : "§cDisabled"), true, () -> {
            this.stagedEnabled = !this.stagedEnabled;
            initGui();
        });
        y += ROW_HEIGHT + 2;

        int start = this.page * rowsPerPage;
        int end = Math.min(this.packs.size(), start + rowsPerPage);
        for (int i = start; i < end; i++) {
            String name = this.packs.get(i);
            boolean staged = name.equals(this.stagedSelection);
            String label = staged ? (this.stagedEnabled ? "§e" + name : "§7" + name + " §8(off)") : name;
            addRow(rowLeft, y, label, true, () -> {
                this.stagedSelection = name;
                this.stagedEnabled = true;
                initGui();
            });
            y += ROW_HEIGHT;
        }

        if (totalPages > 1) {
            int navY = this.height - 76;
            FlatButtonWidget prev = addButton(this.width / 2 - 104, navY, 100, 20, "< Prev", () -> {
                this.page--;
                initGui();
            });
            FlatButtonWidget next = addButton(this.width / 2 + 4, navY, 100, 20, "Next >", () -> {
                this.page++;
                initGui();
            });
            prev.setEnabled(this.page > 0);
            next.setEnabled(this.page < totalPages - 1);
        }

        int utilY = this.height - 52;
        addButton(this.width / 2 - 154, utilY, 150, 20, "Open Shader Pack Folder...", this::openPackFolder);
        FlatButtonWidget settings = addButton(this.width / 2 + 4, utilY, 150, 20, "Shader Pack Settings...",
                () -> this.mc.displayGuiScreen(new ShaderPackConfigScreen(this)));
        settings.setEnabled(Umbra.isShaderPackInUse());

        int actY = this.height - 28;
        addButton(this.width / 2 - 154, actY, 100, 20, "Cancel", () -> this.mc.displayGuiScreen(this.parent));
        addButton(this.width / 2 - 50, actY, 100, 20, "Apply", this::applySelection);
        addButton(this.width / 2 + 54, actY, 100, 20, "Done", () -> {
            applySelection();
            this.mc.displayGuiScreen(this.parent);
        });
    }

    // Adds a pack row.
    private FlatButtonWidget addRow(int x, int y, String label, boolean enabled, Runnable action) {
        FlatButtonWidget button = new FlatButtonWidget(new Dim2i(x, y, ROW_WIDTH, 20), TextComponent.literal(label), action);
        button.setEnabled(enabled);
        this.widgets.add(button);
        return button;
    }

    // Adds a button.
    private FlatButtonWidget addButton(int x, int y, int w, int h, String label, Runnable action) {
        FlatButtonWidget button = new FlatButtonWidget(new Dim2i(x, y, w, h), TextComponent.literal(label), action);
        this.widgets.add(button);
        return button;
    }

    // Applies the selection and remembers a pack picked while shaders are off.
    private void applySelection() {
        if (!Umbra.selectShaderpack(this.stagedSelection, this.stagedEnabled && !UmbraConfig.NO_PACK.equals(this.stagedSelection))) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
        initGui();
    }

    // Opens the pack folder in the file manager.
    private void openPackFolder() {
        if (Umbra.getConfig() == null) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(Umbra.getConfig().getShaderpacksDirectory().toFile());
            }
        } catch (Exception e) {
            Umbra.logger().warn("Failed to open shaderpacks folder", e);
        }
    }

    // Forwards a click to the widgets.
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        for (AbstractWidget widget : new ArrayList<>(this.widgets)) {
            if (widget.mouseClicked(VintageInteractionContext.INSTANCE, mouseX, mouseY, mouseButton)) {
                return;
            }
        }
    }

    // Draws the screen.
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        DrawContext ctx = this.drawContext;

        this.drawCenteredString(this.fontRenderer, "Shader Packs", this.width / 2, 12, 0xFFFFFFFF);
        this.drawCenteredString(this.fontRenderer, "§7Select", this.width / 2, 24, 0xFFAAAAAA);

        for (AbstractWidget widget : this.widgets) {
            widget.render(ctx, mouseX, mouseY, partialTicks);
        }
    }

    // Draws the blurred backdrop.
    @Override
    public void drawWorldBackground(int tint) {
        if (this.mc.world != null) {
            com.bdmajora.impetus.impl.gui.ScreenBlurBackdrop.draw(this.width, this.height);
            return;
        }

        super.drawWorldBackground(tint);
    }
}
