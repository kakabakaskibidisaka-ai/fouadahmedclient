package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

public class Fouadtags extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The size of the nametags.")
        .defaultValue(1.0)
        .min(0.1)
        .build()
    );

    private final Setting<Double> smoothSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("smooth-speed")
        .description("How fast the health bar slides.")
        .defaultValue(0.1)
        .range(0.01, 0.5)
        .build()
    );

    private final Setting<Boolean> distanceScaling = sgGeneral.add(new BoolSetting.Builder()
        .name("distance-scaling")
        .description("Smaller tags for distant players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> throughWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("See tags through blocks.")
        .defaultValue(true)
        .build()
    );

    private final Map<PlayerEntity, Double> smoothHealthMap = new HashMap<>();

    public Fouadtags() {
        super(AddonTemplate.CATEGORY, "fouadtags", "Clean UI nametags with smooth animations.");
    }

    @Override
    public void onDeactivate() {
        smoothHealthMap.clear();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;

            if (!throughWalls.get() && !mc.player.canSee(player)) continue;

            // 1. Position & Scaling
            double dist = mc.player.distanceTo(player);
            double finalScale = scale.get();
            if (distanceScaling.get()) {
                finalScale *= Math.max(0.45, 1.0 - (dist / 100.0));
            }

            // Interpolated 3D position to 2D
            double lerpX = MathHelper.lerp(event.tickDelta, player.prevX, player.getX());
            double lerpY = MathHelper.lerp(event.tickDelta, player.prevY, player.getY());
            double lerpZ = MathHelper.lerp(event.tickDelta, player.prevZ, player.getZ());
            
            Vec3d pos3D = new Vec3d(lerpX, lerpY + player.getHeight() + 0.5, lerpZ);
            Vector3d screenPos = new Vector3d();

            if (NametagUtils.toScreen(pos3D, screenPos)) {
                renderFouadTag(event.renderer, player, screenPos.x, screenPos.y, finalScale);
            }
        }
    }

    private void renderFouadTag(Renderer2D renderer, PlayerEntity player, double x, double y, double scale) {
        // --- Setup Sizes ---
        double width = 140 * scale;
        double height = 45 * scale;
        double headSize = 32 * scale;
        double pad = 6 * scale;

        double rX = x - (width / 2);
        double rY = y - height;

        // Background (Dark & Clean)
        renderer.quad(rX, rY, width, height, new Color(10, 10, 10, 190));

        // 1. Draw Player Face
        if (player instanceof AbstractClientPlayerEntity clientPlayer) {
            Identifier skin = clientPlayer.getSkinTextures().texture();
            renderer.texture(rX + pad, rY + pad, headSize, headSize, 8d/64d, 8d/64d, 16d/64d, 16d/64d, skin);
            renderer.texture(rX + pad, rY + pad, headSize, headSize, 40d/64d, 8d/64d, 48d/64d, 16d/64d, skin);
        }

        // 2. Name
        String name = player.getEntityName();
        renderer.text(name, rX + headSize + (pad * 2), rY + pad, Color.WHITE, scale);

        // 3. Smooth Health Logic
        double target = MathHelper.clamp(player.getHealth() / player.getMaxHealth(), 0, 1);
        double smooth = smoothHealthMap.getOrDefault(player, target);
        smooth += (target - smooth) * smoothSpeed.get();
        smoothHealthMap.put(player, smooth);

        // 4. Health Bar (The "Slider")
        double barX = rX + headSize + (pad * 2);
        double barY = rY + (height / 2) + (2 * scale);
        double barW = 65 * scale;
        double barH = 5 * scale;

        // Bar Background
        renderer.quad(barX, barY, barW, barH, new Color(45, 35, 15, 255));
        // Sliding Part (Golden/Orange)
        renderer.quad(barX, barY, barW * smooth, barH, new Color(255, 170, 0, 255));

        // 5. Armor Icons
        double armorX = barX;
        double armorY = barY + barH + (3 * scale);
        for (ItemStack stack : player.getArmorItems()) {
            if (stack.isEmpty()) continue;
            renderer.item(armorX, armorY, 0.5f * (float)scale, stack);
            armorX += 15 * scale;
        }

        // 6. Health Number
        String hpString = String.valueOf((int) player.getHealth());
        renderer.text(hpString, rX + width - (25 * scale), rY + (height / 2), Color.WHITE, scale * 0.85);
    }
}
