package me.tochuuu.reactions.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public final class ReactionsConfigScreen extends Screen {
    private static final int FACE_U = 8;
    private static final int FACE_V = 8;
    private static final int FACE_PIXELS = 8;
    private static final int SKIN_SIZE = 64;
    private static final int FACE_SIZE = 192;
    private static final int PIXEL_SIZE = FACE_SIZE / FACE_PIXELS;
    private static final int PANEL_WIDTH = 168;
    private static final int MAX_EYE_WIDTH = 2;
    private static final int MAX_EYE_HEIGHT = 3;
    private static final int SIZE_LIMIT_MESSAGE_TICKS = 60;

    private final Screen parent;
    private EditMode mode = EditMode.LEFT_EYE;
    private int faceX;
    private int faceY;
    private int modeHeaderY;
    private int animationHeaderY;
    private int eyeSizeHeaderY;
    private int sizeLimitMessageTicks;

    public ReactionsConfigScreen(Screen parent) {
        super(Component.literal("Reactions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int contentWidth = FACE_SIZE + 24 + PANEL_WIDTH;
        faceX = Math.max(12, this.width / 2 - contentWidth / 2);
        faceY = 48;
        int panelX = faceX + FACE_SIZE + 24;
        int y = faceY;

        addRenderableWidget(Button.builder(enabledText(), button -> {
            ReactionsClientConfig.get().enabled = !ReactionsClientConfig.get().enabled;
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(panelX, y, PANEL_WIDTH, 20).build());

        y += 28;
        modeHeaderY = y - 12;
        addModeButton(EditMode.LEFT_EYE, panelX, y);
        y += 24;
        addModeButton(EditMode.RIGHT_EYE, panelX, y);
        y += 24;
        addModeButton(EditMode.EYEDROPPER, panelX, y);

        y += 34;
        animationHeaderY = y - 12;
        addRenderableWidget(Button.builder(selfAnimationText(), button -> {
            ReactionsClientConfig.get().animateSelf = !ReactionsClientConfig.get().animateSelf;
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(panelX, y, PANEL_WIDTH, 20).build());

        y += 24;
        addRenderableWidget(Button.builder(otherAnimationText(), button -> {
            ReactionsClientConfig.get().animateOthers = !ReactionsClientConfig.get().animateOthers;
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(panelX, y, PANEL_WIDTH, 20).build());

        y += 34;
        eyeSizeHeaderY = y - 12;
        addSizeButton("Width", panelX, y, true, -1);
        addSizeButton("Width", panelX + 88, y, true, 1);
        y += 24;
        addSizeButton("Height", panelX, y, false, -1);
        addSizeButton("Height", panelX + 88, y, false, 1);

        int buttonY = this.height - 30;
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            ReactionsClientConfig.reset();
            mode = EditMode.LEFT_EYE;
            rebuildWidgets();
        }).bounds(this.width / 2 - 105, buttonY, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
            ReactionsClientConfig.save();
            onClose();
        }).bounds(this.width / 2 + 15, buttonY, 90, 20).build());
    }

    private void addModeButton(EditMode targetMode, int x, int y) {
        addRenderableWidget(Button.builder(modeText(targetMode), button -> {
            mode = targetMode;
            rebuildWidgets();
        }).bounds(x, y, PANEL_WIDTH, 20).build());
    }

    private void addSizeButton(String label, int x, int y, boolean width, int delta) {
        addRenderableWidget(Button.builder(Component.literal(label + " " + (delta < 0 ? "-" : "+")), button -> {
            ReactionsClientConfig config = ReactionsClientConfig.get();
            if (width) {
                if (delta > 0 && config.eyeWidth >= MAX_EYE_WIDTH) {
                    showSizeLimitMessage();
                    return;
                }
                config.eyeWidth += delta;
            } else {
                if (delta > 0 && config.eyeHeight >= MAX_EYE_HEIGHT) {
                    showSizeLimitMessage();
                    return;
                }
                config.eyeHeight += delta;
            }
            ReactionsClientConfig.save();
            rebuildWidgets();
        }).bounds(x, y, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ReactionsClientConfig config = ReactionsClientConfig.get();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 16, 0xFFFFFF);

        Identifier texture = skinTexture();
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, faceX, faceY, FACE_U, FACE_V, FACE_SIZE, FACE_SIZE, FACE_PIXELS, FACE_PIXELS, SKIN_SIZE, SKIN_SIZE);
        drawGrid(graphics);
        drawEyeSelection(graphics, config.leftEyeX, config.leftEyeY, config.eyeWidth, config.eyeHeight, 0xFF43D17C);
        drawEyeSelection(graphics, config.rightEyeX, config.rightEyeY, config.eyeWidth, config.eyeHeight, 0xFF4AA3FF);
        drawPixelMarker(graphics, config.eyelidColorX, config.eyelidColorY, 0xFFFFC94A);

        int labelY = faceY + FACE_SIZE + 10;
        graphics.drawString(this.font, Component.literal("Click the face to set " + mode.label), faceX, labelY, 0xFFFFFF);
        graphics.drawString(this.font, Component.literal("Eyes: " + config.eyeWidth + "x" + config.eyeHeight), faceX, labelY + 12, 0xBFC7D5);
        graphics.drawString(this.font, Component.literal("Eyelid color UV: " + config.eyelidColorX + ", " + config.eyelidColorY), faceX, labelY + 24, 0xBFC7D5);
        if (sizeLimitMessageTicks > 0) {
            graphics.drawString(this.font, Component.literal("Cannot make eyes bigger"), faceX, labelY + 36, 0xFFFF4040);
        }

        int panelX = faceX + FACE_SIZE + 24;
        graphics.drawString(this.font, Component.literal("Mode"), panelX, modeHeaderY, 0xBFC7D5);
        graphics.drawString(this.font, Component.literal("Animation"), panelX, animationHeaderY, 0xBFC7D5);
        graphics.drawString(this.font, Component.literal("Eye size"), panelX, eyeSizeHeaderY, 0xBFC7D5);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isInsideFace(event.x(), event.y())) {
            int skinX = FACE_U + (int) ((event.x() - faceX) / PIXEL_SIZE);
            int skinY = FACE_V + (int) ((event.y() - faceY) / PIXEL_SIZE);
            applyFaceClick(skinX, skinY);
            ReactionsClientConfig.save();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void applyFaceClick(int skinX, int skinY) {
        ReactionsClientConfig config = ReactionsClientConfig.get();
        if (mode == EditMode.LEFT_EYE) {
            config.leftEyeX = skinX;
            config.leftEyeY = skinY;
        } else if (mode == EditMode.RIGHT_EYE) {
            config.rightEyeX = skinX;
            config.rightEyeY = skinY;
        } else {
            config.eyelidColorX = skinX;
            config.eyelidColorY = skinY;
        }
    }

    private void drawGrid(GuiGraphics graphics) {
        for (int i = 0; i <= FACE_PIXELS; i++) {
            int line = faceX + i * PIXEL_SIZE;
            graphics.fill(line, faceY, line + 1, faceY + FACE_SIZE, 0x66000000);
            line = faceY + i * PIXEL_SIZE;
            graphics.fill(faceX, line, faceX + FACE_SIZE, line + 1, 0x66000000);
        }
        graphics.renderOutline(faceX, faceY, FACE_SIZE, FACE_SIZE, 0xFFFFFFFF);
    }

    private void drawEyeSelection(GuiGraphics graphics, int skinX, int skinY, int width, int height, int color) {
        int x = faceX + (skinX - FACE_U) * PIXEL_SIZE;
        int y = faceY + (skinY - FACE_V) * PIXEL_SIZE;
        int w = width * PIXEL_SIZE;
        int h = height * PIXEL_SIZE;
        graphics.fill(x, y, x + w, y + h, color & 0x55FFFFFF);
        graphics.renderOutline(x, y, w, h, color);
    }

    private void drawPixelMarker(GuiGraphics graphics, int skinX, int skinY, int color) {
        int x = faceX + (skinX - FACE_U) * PIXEL_SIZE;
        int y = faceY + (skinY - FACE_V) * PIXEL_SIZE;
        if (x < faceX || y < faceY || x >= faceX + FACE_SIZE || y >= faceY + FACE_SIZE) {
            return;
        }
        graphics.fill(x + 7, y + 7, x + PIXEL_SIZE - 7, y + PIXEL_SIZE - 7, color);
        graphics.renderOutline(x + 5, y + 5, PIXEL_SIZE - 10, PIXEL_SIZE - 10, 0xFF000000);
    }

    private Identifier skinTexture() {
        if (this.minecraft != null && this.minecraft.player != null) {
            return this.minecraft.player.getSkin().body().texturePath();
        }
        return MinecraftFallbacks.DEFAULT_SKIN;
    }

    private boolean isInsideFace(double mouseX, double mouseY) {
        return mouseX >= faceX && mouseX < faceX + FACE_SIZE && mouseY >= faceY && mouseY < faceY + FACE_SIZE;
    }

    private Component enabledText() {
        return Component.literal("Mod: " + (ReactionsClientConfig.get().enabled ? "Enabled" : "Disabled"));
    }

    private Component selfAnimationText() {
        return Component.literal("Self animations: " + onOff(ReactionsClientConfig.get().animateSelf));
    }

    private Component otherAnimationText() {
        return Component.literal("Other animations: " + onOff(ReactionsClientConfig.get().animateOthers));
    }

    private Component modeText(EditMode targetMode) {
        return Component.literal((mode == targetMode ? "> " : "") + targetMode.label);
    }

    private static String onOff(boolean enabled) {
        return enabled ? "On" : "Off";
    }

    @Override
    public void tick() {
        if (sizeLimitMessageTicks > 0) {
            sizeLimitMessageTicks--;
        }
    }

    private void showSizeLimitMessage() {
        sizeLimitMessageTicks = SIZE_LIMIT_MESSAGE_TICKS;
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.5F);
        }
    }

    @Override
    public void onClose() {
        ReactionsClientConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private enum EditMode {
        LEFT_EYE("left eye"),
        RIGHT_EYE("right eye"),
        EYEDROPPER("eyelid color");

        private final String label;

        EditMode(String label) {
            this.label = label;
        }
    }

    private static final class MinecraftFallbacks {
        private static final Identifier DEFAULT_SKIN = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
    }
}
