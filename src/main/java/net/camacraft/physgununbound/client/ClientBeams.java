package net.camacraft.physgununbound.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.camacraft.physgununbound.hold.HolderScale;
import net.camacraft.physgununbound.network.BeamMessage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The physgun beam to a held entity, drawn the way VMod draws it to a held ship
 * ({@code PhysgunRayRenderer}): a translucent blue tube (0, 0, 255 at alpha 100, 0.1 blocks
 * across, four-sided, full bright) along a quadratic curve of 32 segments. It leaves the gun (in
 * first person: 0.2 below, 0.6 right of and 0.45 in front of the camera; otherwise the holder's
 * eyes), bends out along the look for half the distance to the held point, then curves in to the
 * grabbed point on the entity, so it sags toward the crosshair while the entity lags behind.
 *
 * <p>Drawn at the holder's size ({@link HolderScale#size}): a 1/16 player (VS Genesis's Great Unknown) draws a tube
 * 1/16 as thick, leaving the gun 1/16 as far from the camera, so it looks the same against its world as a full-size
 * beam does against a full-size one.
 */
public final class ClientBeams {
    private static final int SEGMENTS = 32;
    private static final int SIDES = 4;
    private static final double RADIUS = 0.1;
    private static final int RED = 0, GREEN = 0, BLUE = 255, ALPHA = 100;
    /** A beam not refreshed for this many ticks has ended (its end message was missed). */
    private static final int TIMEOUT = 10;

    private record Beam(int entityId, Vec3 offset, long seen) {}

    /** One beam's points and how thick it is drawn. */
    private record Curve(Vec3[] points, double radius) {}

    private static final Map<Integer, Beam> BEAMS = new HashMap<>();
    private static final BufferBuilder BUFFER = new BufferBuilder(4096);
    private static long ticks;

    private ClientBeams() {}

    public static void onMessage(BeamMessage m) {
        if (m.active()) {
            BEAMS.put(m.holderId(), new Beam(m.entityId(), new Vec3(m.offsetX(), m.offsetY(), m.offsetZ()), ticks));
            ClientModelRotations.held(m.entityId(), m.orientation());
        } else {
            Beam ended = BEAMS.remove(m.holderId());
            if (ended != null) ClientModelRotations.release(ended.entityId());
        }
    }

    public static void tick() {
        ticks++;
        if (!BEAMS.isEmpty()) BEAMS.values().removeIf(beam -> ticks - beam.seen() > TIMEOUT);
    }

    public static void clear() {
        BEAMS.clear();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BEAMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Camera camera = event.getCamera();
        float partialTick = event.getPartialTick();
        Vec3 cam = camera.getPosition();
        Vec3 camUp = new Vec3(camera.getUpVector());

        List<Curve> curves = new ArrayList<>(BEAMS.size());
        for (Map.Entry<Integer, Beam> entry : BEAMS.entrySet()) {
            if (!(level.getEntity(entry.getKey()) instanceof Player holder)) continue;
            Entity held = level.getEntity(entry.getValue().entityId());
            if (held == null) continue;

            double size = HolderScale.size(holder);
            Vec3 start;
            Vec3 look;
            if (holder == mc.player && !camera.isDetached()) {
                look = new Vec3(camera.getLookVector()).normalize();
                Vec3 up = new Vec3(camera.getUpVector());
                Vec3 left = new Vec3(camera.getLeftVector());
                start = cam.subtract(up.scale(0.2 * size)).subtract(left.scale(0.6 * size)).add(look.scale(0.45 * size));
            } else {
                start = holder.getEyePosition(partialTick);
                look = holder.getViewVector(partialTick).normalize();
            }
            Vec3 end = held.getPosition(partialTick).add(entry.getValue().offset());
            double along = Math.abs(end.subtract(start).dot(look));
            Vec3 middle = start.add(look.scale(along / 2.0));
            curves.add(new Curve(curve(start, middle, end), RADIUS * size));
        }
        if (curves.isEmpty()) return;

        // vertices relative to the camera, so floats keep their precision far from the origin
        PoseStack pose = event.getPoseStack();
        Matrix4f matrix = pose.last().pose();
        BUFFER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Curve c : curves) tube(matrix, c.points(), c.radius(), camUp, cam);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        BufferUploader.drawWithShader(BUFFER.end());
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** The quadratic curve through start, bent toward middle, to end: SEGMENTS + 1 points. */
    private static Vec3[] curve(Vec3 start, Vec3 middle, Vec3 end) {
        Vec3[] points = new Vec3[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            double f = (double) i / SEGMENTS;
            Vec3 a = start.lerp(middle, f);
            Vec3 b = middle.lerp(end, f);
            points[i] = a.lerp(b, f);
        }
        return points;
    }

    /** A four-sided tube along the points, its faces wound to face out (so back faces cull). */
    private static void tube(Matrix4f matrix, Vec3[] points, double radius, Vec3 camUp, Vec3 cam) {
        Vec3[] previous = null;
        for (int i = 0; i < points.length; i++) {
            Vec3 dir = i + 1 < points.length ? points[i + 1].subtract(points[i]) : points[i].subtract(points[i - 1]);
            if (dir.lengthSqr() < 1.0E-12) dir = new Vec3(0.0, 0.0, 1.0);
            dir = dir.normalize();
            Vec3 u = camUp.subtract(dir.scale(dir.dot(camUp)));
            if (u.lengthSqr() < 1.0E-8) u = new Vec3(1.0, 0.0, 0.0).subtract(dir.scale(dir.x));
            u = u.normalize();
            Vec3 v = dir.cross(u); // u x v = dir: rings wind counter-clockwise seen from ahead
            Vec3[] ring = new Vec3[SIDES];
            for (int k = 0; k < SIDES; k++) {
                double angle = Math.PI * 2.0 * k / SIDES;
                ring[k] = points[i].add(u.scale(Math.cos(angle) * radius)).add(v.scale(Math.sin(angle) * radius));
            }
            if (previous != null) {
                for (int k = 0; k < SIDES; k++) {
                    int n = (k + 1) % SIDES;
                    vertex(matrix, previous[k], cam);
                    vertex(matrix, previous[n], cam);
                    vertex(matrix, ring[n], cam);
                    vertex(matrix, ring[k], cam);
                }
            }
            previous = ring;
        }
    }

    private static void vertex(Matrix4f matrix, Vec3 p, Vec3 cam) {
        BUFFER.vertex(matrix, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z))
                .color(RED, GREEN, BLUE, ALPHA).endVertex();
    }
}
