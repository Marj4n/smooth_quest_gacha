package org.Marj4n.smooth_quest_gacha.client;

import org.Marj4n.smooth_quest_gacha.gacha.GachaRarity;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GachaScreen extends Screen {

    public record Result(ItemStack stack, GachaRarity rarity) {
        public Result { stack = stack.copy(); }
    }

    public record PoolSymbol(ItemStack stack, GachaRarity rarity, int weight) {
        public PoolSymbol {
            stack = stack.copy();
            weight = Math.max(1, weight);
        }
    }

    private static final int PER_PAGE = 8;
    private static final int SPIN_TICKS = 92;
    private static final int REVEAL_STAGGER = 4;
    private static final int ROW_SPACING = 27;
    private static final int CONFETTI_DURATION = 100;
    private static final int CONFETTI_COUNT = 240;

    private final List<Result> results;
    private final List<PoolSymbol> poolSymbols;
    private int page = 0;
    private int tick = 0;
    private boolean pageFinished = false;
    private int celebrationStartTick = -1;
    private final List<Confetti> confetti = new ArrayList<>();

    private static final class Confetti {
        final float x;
        final float y;
        final float vx;
        final float vy;
        final int color;
        final int width;
        final int height;

        Confetti(float x, float y, float vx, float vy, int color, int width, int height) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.width = width;
            this.height = height;
        }
    }

    public GachaScreen(List<Result> results, List<PoolSymbol> poolSymbols) {
        super(Text.literal("Smooth Gacha"));
        this.results = new ArrayList<>(results);
        this.poolSymbols = new ArrayList<>(poolSymbols);

        // Defensive fallback: an old/empty packet can still animate using
        // the actual results instead of unrelated vanilla filler items.
        if (this.poolSymbols.isEmpty()) {
            for (Result result : results) {
                this.poolSymbols.add(new PoolSymbol(result.stack(), result.rarity(), 1));
            }
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void tick() {
        super.tick();

        // Keep the screen clock running even after the reels have finished.
        // Legendary confetti uses this same clock for gravity, falling and
        // lifetime/fade. Previously tick stopped as soon as pageFinished became
        // true, which froze every confetti piece forever at its first position.
        tick++;

        if (!pageFinished) {
            int visible = getPageResults().size();
            if (tick >= SPIN_TICKS + Math.max(0, visible - 1) * REVEAL_STAGGER) {
                pageFinished = true;
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        List<Result> pageResults = getPageResults();
        int count = pageResults.size();
        if (count == 0) return;

        int centerX = width / 2;
        int centerY = height / 2;
        int spacing = Math.min(58, Math.max(38, (width - 80) / Math.max(1, count)));
        int totalWidth = (count - 1) * spacing;
        int startX = centerX - totalWidth / 2;

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("Gacha Pull  " + (page + 1) + "/" + getPageCount()),
                centerX,
                Math.max(20, centerY - 112),
                0xFFFFFF
        );

        for (int i = 0; i < count; i++) {
            renderReel(context, pageResults.get(i), i, startX + i * spacing, centerY, delta);
        }

        updateLegendaryCelebration(pageResults);
        renderLegendaryCelebration(context, centerX, centerY, delta);

        String footer;
        if (!pageFinished) {
            footer = "[ SPACE ] Skip     [ ESC ] Reveal All";
        } else if (hasNextPage()) {
            footer = "[ SPACE / CLICK ] Next " + Math.min(PER_PAGE, results.size() - (page + 1) * PER_PAGE) + " Pulls";
        } else {
            footer = "[ SPACE / CLICK / ESC ] Close";
        }

        // Arcade/gacha-machine style blinking prompt. Smooth pulse instead of
        // hard on/off flicker so it remains readable.
        float pulse = (float) ((Math.sin((tick + delta) * 0.22D) + 1.0D) * 0.5D);
        int gray = 150 + Math.round(105 * pulse);
        int footerColor = (gray << 16) | (gray << 8) | gray;
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(footer),
                centerX,
                Math.min(height - 24, centerY + 112),
                footerColor
        );

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderReel(DrawContext context, Result result, int reelIndex, int x, int centerY, float delta) {
        int revealTick = SPIN_TICKS + reelIndex * REVEAL_STAGGER;
        boolean revealed = pageFinished || tick >= revealTick;

        context.fill(x - 18, centerY - 62, x + 18, centerY + 63, 0x66000000);

        if (revealed) {
            int color = rarityColor(result.rarity());
            context.fill(x - 20, centerY - 20, x + 20, centerY + 20, 0xAA000000 | (color & 0x00FFFFFF));
            context.fill(x - 18, centerY - 18, x + 18, centerY + 18, 0xDD101010);
            drawScaledItem(context, result.stack(), x, centerY, 1.45f);

            context.drawCenteredTextWithShadow(textRenderer, Text.literal(shortRarity(result.rarity())), x, centerY + 31, color);
            if (result.stack().getCount() > 1) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("x" + result.stack().getCount()), x, centerY + 43, 0xDDDDDD);
            }
            return;
        }

        // Pachinko / slot-machine motion with a deterministic landing.
        // The reel is no longer replaced by the result at reveal time. Instead,
        // its final travel distance is calculated so the actual rolled item is
        // physically sitting in the selection frame when the reel stops.
        float localTime = Math.max(0.0f, tick + delta - reelIndex * 1.25f);
        float clampedTime = Math.min(localTime, SPIN_TICKS);
        float progress = clampedTime / SPIN_TICKS;

        // Quintic ease-out: very fast launch, then progressively slower motion,
        // reaching exactly zero velocity at the destination.
        float eased = 1.0f - (float) Math.pow(1.0f - progress, 5.0f);

        int targetPoolIndex = findPoolIndex(result);

        // row == 0 renders symbolAt(baseIndex + reelIndex * 3). Choose a final
        // base index congruent with the rolled result, then add whole pool loops.
        // Whole loops never change the final symbol, but make the reel spin long
        // enough to feel like a real machine.
        int poolSize = Math.max(1, poolSymbols.size());
        int finalBaseModulo = Math.floorMod(targetPoolIndex - reelIndex * 3, poolSize);
        int loops = 5 + reelIndex;
        int finalBaseIndex = finalBaseModulo + loops * poolSize;
        float targetTravel = finalBaseIndex * ROW_SPACING;

        float travel = targetTravel * eased;
        int baseIndex = (int) Math.floor(travel / ROW_SPACING);
        float phase = travel - baseIndex * ROW_SPACING;

        // Six symbols keeps the reel filled while moving. Every symbol comes
        // from the selected config pool: no unrelated filler items.
        for (int row = -3; row <= 2; row++) {
            float y = centerY + row * ROW_SPACING + phase;
            PoolSymbol symbol = symbolAt(baseIndex - row + reelIndex * 3);
            float distance = Math.abs(y - centerY);
            float scale = distance < 14 ? 1.18f : (distance < 42 ? 0.90f : 0.72f);
            drawScaledItem(context, symbol.stack(), x, Math.round(y), scale);
        }

        drawSelectionFrame(context, x, centerY);
    }

    private void updateLegendaryCelebration(List<Result> pageResults) {
        if (celebrationStartTick >= 0) return;

        for (int i = 0; i < pageResults.size(); i++) {
            Result result = pageResults.get(i);
            if (result.rarity() != GachaRarity.LEGENDARY) continue;

            int revealTick = SPIN_TICKS + i * REVEAL_STAGGER;
            if (pageFinished || tick >= revealTick) {
                celebrationStartTick = tick;
                createConfetti();
                return;
            }
        }
    }

    private void createConfetti() {
        confetti.clear();
        Random random = new Random(System.nanoTime() ^ (page * 9973L));
        int[] colors = {
                0xFFFFD54F, 0xFFFF5252, 0xFF69F0AE, 0xFF40C4FF,
                0xFFE040FB, 0xFFFFFFFF, 0xFFFF9100, 0xFFFF4081,
                0xFF7C4DFF, 0xFFB2FF59
        };

        // Two real confetti cannons from the lower corners. The values are
        // normalized so the effect scales with every GUI resolution.
        for (int i = 0; i < CONFETTI_COUNT; i++) {
            boolean left = (i & 1) == 0;
            float x = left ? 0.08f + random.nextFloat() * 0.10f
                           : 0.82f + random.nextFloat() * 0.10f;
            float y = 0.82f + random.nextFloat() * 0.10f;

            // Velocity is stored in screen-fraction/tick. Both cannons shoot
            // inward and strongly upward, then gravity pulls the pieces down.
            float vx = (left ? 1.0f : -1.0f) * (0.0035f + random.nextFloat() * 0.0075f);
            float vy = -(0.020f + random.nextFloat() * 0.022f);
            int color = colors[random.nextInt(colors.length)];
            int w = 2 + random.nextInt(5);
            int h = 3 + random.nextInt(7);
            confetti.add(new Confetti(x, y, vx, vy, color, w, h));
        }

        // Extra firework-like burst around the legendary banner so the first
        // frame is immediately celebratory instead of waiting for pieces to fall.
        for (int i = 0; i < 90; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            float speed = 0.003f + random.nextFloat() * 0.010f;
            float x = 0.50f + (random.nextFloat() - 0.5f) * 0.035f;
            float y = 0.30f + (random.nextFloat() - 0.5f) * 0.035f;
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed - 0.004f;
            int color = colors[random.nextInt(colors.length)];
            confetti.add(new Confetti(x, y, vx, vy, color, 2 + random.nextInt(4), 2 + random.nextInt(5)));
        }
    }

    private void renderLegendaryCelebration(DrawContext context, int centerX, int centerY, float delta) {
        if (celebrationStartTick < 0) return;

        float age = Math.max(0.0f, tick + delta - celebrationStartTick);
        if (age > CONFETTI_DURATION) return;

        // Very short flash only. The old long yellow overlay hid the confetti.
        if (age < 4.0f) {
            float triangle = age < 1.0f ? age : (4.0f - age) / 3.0f;
            int alpha = Math.max(0, Math.min(75, Math.round(triangle * 75.0f)));
            context.fill(0, 0, width, height, (alpha << 24) | 0x00FFD54F);
        }

        // Draw confetti after the flash so it always sits visibly on top.
        // Physics are normalized to screen size: launch -> apex -> gravity fall.
        final float gravity = 0.00072f;
        for (int i = 0; i < confetti.size(); i++) {
            Confetti c = confetti.get(i);
            float sway = (float) Math.sin(age * 0.24f + i * 0.71f) * 0.006f;
            float px = (c.x + c.vx * age + sway) * width;
            float py = (c.y + c.vy * age + 0.5f * gravity * age * age) * height;

            if (px < -20 || px > width + 20 || py < -20 || py > height + 20) continue;

            // Fake tumbling by alternating between a tall strip and a wide strip.
            boolean flipped = ((int) (age / 3.0f) + i) % 2 == 0;
            int cw = flipped ? c.width : c.height;
            int ch = flipped ? c.height : Math.max(2, c.width);
            int x = Math.round(px);
            int y = Math.round(py);
            context.fill(x, y, x + cw, y + ch, c.color);

            // Tiny bright center on some pieces gives a firework sparkle without
            // spawning any Minecraft world particles.
            if ((i % 7) == 0 && age < 35.0f) {
                context.fill(x - 1, y + ch / 2, x + cw + 1, y + ch / 2 + 1, 0xFFFFFFFF);
            }
        }

        // Big celebratory label, rendered last so the confetti never obscures it.
        float bounce = age < 18.0f
                ? 1.0f + (float) Math.sin(age * 0.72f) * (1.0f - age / 18.0f) * 0.24f
                : 1.0f;
        float pulse = 1.0f + (float) Math.sin(age * 0.18f) * 0.035f;
        float labelScale = bounce * pulse;
        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY - 91, 300);
        context.getMatrices().scale(labelScale, labelScale, 1.0f);
        context.drawCenteredTextWithShadow(
                textRenderer, Text.literal("LEGENDARY!!!"), 0, 0, 0xFFFF55
        );
        context.getMatrices().pop();
    }

    private PoolSymbol symbolAt(int index) {
        return poolSymbols.get(Math.floorMod(index, poolSymbols.size()));
    }

    private int findPoolIndex(Result result) {
        for (int i = 0; i < poolSymbols.size(); i++) {
            PoolSymbol symbol = poolSymbols.get(i);
            if (symbol.stack().getItem() == result.stack().getItem()
                    && symbol.rarity() == result.rarity()) {
                return i;
            }
        }

        // Defensive fallback for old packets/config changes. Normally every
        // rolled result is guaranteed to exist in the pool sent by the server.
        return 0;
    }

    private void drawSelectionFrame(DrawContext context, int x, int centerY) {
        context.fill(x - 20, centerY - 20, x - 18, centerY + 20, 0xFFFFFFFF);
        context.fill(x + 18, centerY - 20, x + 20, centerY + 20, 0xFFFFFFFF);
        context.fill(x - 20, centerY - 20, x + 20, centerY - 18, 0xFFFFFFFF);
        context.fill(x - 20, centerY + 18, x + 20, centerY + 20, 0xFFFFFFFF);
    }

    private void drawScaledItem(DrawContext context, ItemStack stack, int centerX, int centerY, float scale) {
        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawItem(stack, -8, -8);
        context.getMatrices().pop();
    }

    private int rarityColor(GachaRarity rarity) {
        return switch (rarity) {
            case COMMON -> 0xFFFFFF;
            case UNCOMMON -> 0x55FF55;
            case RARE -> 0x55AAFF;
            case EPIC -> 0xAA55FF;
            case LEGENDARY -> 0xFFAA00;
        };
    }

    private String shortRarity(GachaRarity rarity) {
        return switch (rarity) {
            case COMMON -> "Common";
            case UNCOMMON -> "Uncommon";
            case RARE -> "Rare";
            case EPIC -> "Epic";
            case LEGENDARY -> "Legendary";
        };
    }

    private List<Result> getPageResults() {
        int from = page * PER_PAGE;
        int to = Math.min(results.size(), from + PER_PAGE);
        if (from >= to) return List.of();
        return results.subList(from, to);
    }

    private int getPageCount() { return Math.max(1, (results.size() + PER_PAGE - 1) / PER_PAGE); }
    private boolean hasNextPage() { return (page + 1) * PER_PAGE < results.size(); }

    private void revealPage() {
        pageFinished = true;
        tick = SPIN_TICKS + PER_PAGE * REVEAL_STAGGER;
    }

    private void nextOrClose() {
        if (!pageFinished) {
            revealPage();
            return;
        }
        if (hasNextPage()) {
            page++;
            tick = 0;
            pageFinished = false;
            celebrationStartTick = -1;
            confetti.clear();
        } else if (client != null) {
            client.setScreen(null);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        nextOrClose();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
            nextOrClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (!pageFinished || hasNextPage()) {
                page = getPageCount() - 1;
                celebrationStartTick = -1;
                confetti.clear();
                revealPage();
                return true;
            }
            if (client != null) client.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
