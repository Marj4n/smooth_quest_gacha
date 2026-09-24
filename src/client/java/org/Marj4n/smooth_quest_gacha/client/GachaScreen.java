package org.Marj4n.smooth_quest_gacha.client;

import org.Marj4n.smooth_quest_gacha.gacha.GachaRarity;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class GachaScreen extends Screen {

    public record Result(ItemStack stack, GachaRarity rarity) {
        public Result {
            stack = stack.copy();
        }
    }

    private static final int PER_PAGE = 8;
    private static final int SPIN_TICKS = 58;
    private static final int REVEAL_STAGGER = 3;
    private static final int ROW_SPACING = 25;

    private static final List<ItemStack> FILLER_ITEMS = List.of(
            new ItemStack(Items.IRON_INGOT),
            new ItemStack(Items.GOLD_INGOT),
            new ItemStack(Items.REDSTONE),
            new ItemStack(Items.EMERALD),
            new ItemStack(Items.DIAMOND),
            new ItemStack(Items.ENDER_PEARL),
            new ItemStack(Items.AMETHYST_SHARD),
            new ItemStack(Items.BLAZE_ROD),
            new ItemStack(Items.BONE),
            new ItemStack(Items.STRING),
            new ItemStack(Items.GOLDEN_APPLE),
            new ItemStack(Items.NETHERITE_SCRAP),
            new ItemStack(Items.EXPERIENCE_BOTTLE),
            new ItemStack(Items.SLIME_BALL),
            new ItemStack(Items.BOOK),
            new ItemStack(Items.GUNPOWDER)
    );

    private final List<Result> results;
    private int page = 0;
    private int tick = 0;
    private boolean pageFinished = false;

    public GachaScreen(List<Result> results) {
        super(Text.literal("Smooth Gacha"));
        this.results = new ArrayList<>(results);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

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
        if (count == 0) {
            return;
        }

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
            int x = startX + i * spacing;
            renderReel(context, pageResults.get(i), i, x, centerY, delta);
        }

        String footer;
        if (!pageFinished) {
            footer = "SPACE: skip this page    ESC: reveal all";
        } else if (hasNextPage()) {
            footer = "SPACE / Click: next " + Math.min(PER_PAGE, results.size() - (page + 1) * PER_PAGE) + " pulls";
        } else {
            footer = "SPACE / Click / ESC: close";
        }

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(footer),
                centerX,
                Math.min(height - 24, centerY + 112),
                0xAAAAAA
        );

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderReel(
            DrawContext context,
            Result result,
            int reelIndex,
            int x,
            int centerY,
            float delta
    ) {
        int revealTick = SPIN_TICKS + reelIndex * REVEAL_STAGGER;
        boolean revealed = pageFinished || tick >= revealTick;

        // Dark reel lane.
        context.fill(x - 18, centerY - 62, x + 18, centerY + 63, 0x66000000);

        if (revealed) {
            int color = rarityColor(result.rarity());
            context.fill(x - 20, centerY - 20, x + 20, centerY + 20, 0xAA000000 | (color & 0x00FFFFFF));
            context.fill(x - 18, centerY - 18, x + 18, centerY + 18, 0xDD101010);
            drawScaledItem(context, result.stack(), x, centerY, 1.45f);

            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal(shortRarity(result.rarity())),
                    x,
                    centerY + 31,
                    color
            );

            if (result.stack().getCount() > 1) {
                context.drawCenteredTextWithShadow(
                        textRenderer,
                        Text.literal("x" + result.stack().getCount()),
                        x,
                        centerY + 43,
                        0xDDDDDD
                );
            }
            return;
        }

        float speed = reelSpeed(tick, reelIndex);
        float phase = ((tick + delta) * speed + reelIndex * 17.0f) % ROW_SPACING;
        int baseIndex = (int) (((tick * speed) / ROW_SPACING) + reelIndex * 5);

        // Five visible symbols per reel, like a vertical slot machine.
        for (int row = -2; row <= 2; row++) {
            float y = centerY + row * ROW_SPACING + phase - ROW_SPACING / 2.0f;
            int itemIndex = Math.floorMod(baseIndex + row, FILLER_ITEMS.size());
            ItemStack shown = FILLER_ITEMS.get(itemIndex);

            float distance = Math.abs(y - centerY);
            float scale = distance < 13 ? 1.18f : 0.78f;
            drawScaledItem(context, shown, x, Math.round(y), scale);
        }

        // Selection frame in the middle of each reel.
        context.fill(x - 20, centerY - 20, x - 18, centerY + 20, 0xFFFFFFFF);
        context.fill(x + 18, centerY - 20, x + 20, centerY + 20, 0xFFFFFFFF);
        context.fill(x - 20, centerY - 20, x + 20, centerY - 18, 0xFFFFFFFF);
        context.fill(x - 20, centerY + 18, x + 20, centerY + 20, 0xFFFFFFFF);
    }

    private float reelSpeed(int currentTick, int reelIndex) {
        int localTick = Math.max(0, currentTick - reelIndex);
        if (localTick < 20) return 5.8f;
        if (localTick < 36) return 4.2f;
        if (localTick < 48) return 2.8f;
        return 1.45f;
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

    private int getPageCount() {
        return Math.max(1, (results.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private boolean hasNextPage() {
        return (page + 1) * PER_PAGE < results.size();
    }

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
                // ESC skips every remaining reel/page and shows all rewards at once
                // by jumping to the final page in revealed state.
                page = getPageCount() - 1;
                revealPage();
                return true;
            }

            if (client != null) {
                client.setScreen(null);
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
