package com.rlfennec.client;

import com.google.common.collect.ImmutableList;
import com.rlfennec.RLFennec;
import com.rlfennec.input.GamepadMapper;
import com.rlfennec.input.PadAction;
import com.rlfennec.input.PadInput;
import com.rlfennec.registry.ClientConfig;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Where the gamepad bindings are changed: click an action, then press the button / move the stick or trigger you
 * want for it. Escape cancels, Delete unbinds. Duplicates are shown in red like in the vanilla key binds screen.
 */
public class PadBindingsScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("gui.rlfennec.pad.title");

    /** The action waiting for an input, or null. */
    @Nullable
    PadAction listening;
    /** A held trigger / button must be released before the next input is captured. */
    private boolean armed;
    private PadBindingsList list;

    public PadBindingsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, TITLE);
    }

    public PadBindingsScreen(Screen lastScreen) {
        this(lastScreen, Minecraft.getInstance().options);
    }

    @Override
    protected void addOptions() {
    }

    @Override
    protected void addContents() {
        this.list = this.layout.addToContents(new PadBindingsList(this, this.minecraft));
    }

    @Override
    protected void addFooter() {
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(Component.translatable("controls.resetAll"), button -> {
            ClientConfig.resetAll();
            ClientConfig.save();
            this.listening = null;
            this.list.refresh();
        }).build());
        ModList.get().getModContainerById(RLFennec.MOD_ID).ifPresent(container ->
                footer.addChild(Button.builder(Component.translatable("gui.rlfennec.pad.other_options"),
                        button -> this.minecraft.setScreen(new ConfigurationScreen(container, this))).build()));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        this.list.updateSize(this.width, this.layout);
    }

    void startListening(PadAction action) {
        this.listening = action;
        this.armed = false;
        this.list.refresh();
    }

    private void bind(PadAction action, PadInput input) {
        ClientConfig.set(action, input);
        ClientConfig.save();
        this.listening = null;
        this.list.refresh();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.listening == null) {
            return;
        }
        GamepadMapper.State raw = GamepadPoller.readRaw();
        if (raw == null) {
            return;
        }
        PadInput pressed = GamepadMapper.detectPressed(raw);
        if (pressed == null) {
            this.armed = true;
        } else if (this.armed) {
            bind(this.listening, pressed);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.listening != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.listening = null;
                this.list.refresh();
            } else if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                bind(this.listening, PadInput.NONE);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (this.listening != null) {
            // clicking anywhere else cancels the wait
            this.listening = null;
            this.list.refresh();
        }
        return super.mouseClicked(x, y, button);
    }

    Component status() {
        if (this.listening != null) {
            return Component.translatable("gui.rlfennec.pad.listening", Component.translatable(this.listening.translationKey())).withStyle(ChatFormatting.YELLOW);
        }
        String padName = GamepadPoller.name();
        return padName != null
                ? Component.translatable("gui.rlfennec.pad.connected", padName).withStyle(ChatFormatting.GREEN)
                : Component.translatable("gui.rlfennec.pad.none").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public void onClose() {
        this.listening = null;
        super.onClose();
    }

    private static final class PadBindingsList extends ContainerObjectSelectionList<PadBindingsList.Entry> {
        private final PadBindingsScreen screen;

        PadBindingsList(PadBindingsScreen screen, Minecraft minecraft) {
            super(minecraft, screen.width, screen.layout.getContentHeight(), screen.layout.getHeaderHeight(), 22);
            this.screen = screen;
            this.addEntry(new StatusEntry());
            for (PadAction action : PadAction.values()) {
                this.addEntry(new ActionEntry(action));
            }
        }

        void refresh() {
            this.children().forEach(Entry::refresh);
        }

        @Override
        public int getRowWidth() {
            return 340;
        }

        private abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
            void refresh() {
            }
        }

        /** First row: whether a controller is connected, or what the screen is waiting for. */
        private final class StatusEntry extends Entry {
            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY,
                               boolean hovering, float partialTick) {
                graphics.drawCenteredString(PadBindingsList.this.minecraft.font, screen.status(), PadBindingsList.this.width / 2,
                        top + height / 2 - 4, 0xFFFFFFFF);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of();
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of();
            }
        }

        private final class ActionEntry extends Entry {
            private final PadAction action;
            private final Component name;
            private final Button changeButton;
            private final Button resetButton;
            private boolean collision;

            ActionEntry(PadAction action) {
                this.action = action;
                this.name = Component.translatable(action.translationKey());
                this.changeButton = Button.builder(this.name, button -> screen.startListening(action)).bounds(0, 0, 110, 20).build();
                this.resetButton = Button.builder(Component.translatable("controls.reset"), button -> {
                    ClientConfig.set(action, action.defaultInput());
                    ClientConfig.save();
                    PadBindingsList.this.refresh();
                }).bounds(0, 0, 50, 20).build();
                refresh();
            }

            @Override
            void refresh() {
                PadInput input = ClientConfig.bindings().get(this.action);
                this.resetButton.active = !input.equals(this.action.defaultInput());

                MutableComponent others = Component.empty();
                this.collision = false;
                if (!input.isNone()) {
                    for (PadAction other : PadAction.values()) {
                        if (other != this.action && ClientConfig.bindings().get(other).equals(input)) {
                            if (this.collision) {
                                others.append(", ");
                            }
                            this.collision = true;
                            others.append(Component.translatable(other.translationKey()));
                        }
                    }
                }

                Component label = Component.literal(input.displayName());
                if (screen.listening == this.action) {
                    label = Component.literal("> ").append(Component.literal("???").withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                            .append(" <").withStyle(ChatFormatting.YELLOW);
                } else if (this.collision) {
                    label = Component.literal("[ ").append(label.copy().withStyle(ChatFormatting.WHITE)).append(" ]").withStyle(ChatFormatting.RED);
                }
                this.changeButton.setMessage(label);
                this.changeButton.setTooltip(this.collision && screen.listening != this.action
                        ? Tooltip.create(Component.translatable("controls.keybinds.duplicateKeybinds", others)) : null);
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY,
                               boolean hovering, float partialTick) {
                int resetX = PadBindingsList.this.getScrollbarPosition() - this.resetButton.getWidth() - 10;
                this.resetButton.setPosition(resetX, top - 2);
                this.resetButton.render(graphics, mouseX, mouseY, partialTick);
                int changeX = resetX - 5 - this.changeButton.getWidth();
                this.changeButton.setPosition(changeX, top - 2);
                this.changeButton.render(graphics, mouseX, mouseY, partialTick);
                graphics.drawString(PadBindingsList.this.minecraft.font, this.name, left, top + height / 2 - 9 / 2, 0xFFFFFFFF);
                if (this.collision) {
                    graphics.fill(changeX - 6, top - 1, changeX - 3, top + height, 0xFFFF0000);
                }
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return ImmutableList.of(this.changeButton, this.resetButton);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return ImmutableList.of(this.changeButton, this.resetButton);
            }
        }
    }
}
