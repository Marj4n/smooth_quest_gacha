package org.Marj4n.smooth_quest_gacha.client;

import org.Marj4n.smooth_quest_gacha.gacha.GachaRarity;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

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

    private final List<Result> results;
    private final List<PoolSymbol> poolSymbols;
    private int page = 0;
    private int tick = 0;
    private boolean pageFinished = false;

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
        if (!pageFinished) {
            tick++;
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
                revealPage();
                return true;
            }
            if (client != null) client.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
