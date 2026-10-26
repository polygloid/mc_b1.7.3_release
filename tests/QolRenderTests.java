package net.minecraft.src;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;

/** Optional integration checks: requires DISPLAY and matching LWJGL 2 natives. */
public final class QolRenderTests {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class EmptyWorld extends World {
        EmptyWorld() { super(null, "camera-test", new WorldProviderSurface(), 123L); }
        protected IChunkProvider getChunkProvider() { return null; }
        public MovingObjectPosition rayTraceBlocks(Vec3D start, Vec3D end) { return null; }
    }

    public static void main(String[] args) throws Exception {
        Display.setDisplayMode(new DisplayMode(854, 480));
        Display.create();
        File directory = Files.createTempDirectory("beta-qol-render").toFile();
        try {
            GL11.glViewport(0, 0, 854, 480);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
            GL11.glOrtho(0, 427, 240, 0, -1000, 1000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            int[] colors = {0xff0000, 0x00ff00, 0x0000ff, 0xffffff};
            for (int i = 0; i < colors.length; i++) {
                tessellator.setColorOpaque_I(colors[i]);
                int x = i * 100;
                tessellator.addVertex(x, 0, 0); tessellator.addVertex(x, 100, 0);
                tessellator.addVertex(x + 100, 100, 0); tessellator.addVertex(x + 100, 0, 0);
            }
            tessellator.draw();
            ByteBuffer pixel = GLAllocation.createDirectByteBuffer(4);
            for (int i = 0; i < colors.length; i++) {
                GL11.glReadPixels(i * 200 + 100, 400, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
                int actual = (pixel.get(0) & 255) << 16 | (pixel.get(1) & 255) << 8 | pixel.get(2) & 255;
                check(actual == colors[i], "Rendered RGB mismatch: " + Integer.toHexString(actual));
            }
            Minecraft mc = new Minecraft(null, null, null, 854, 480, false) {
                public OpenGlCapsChecker getOpenGlCapsChecker() {
                    return new OpenGlCapsChecker() { public boolean checkARBOcclusion() { return false; } };
                }
                public void displayUnexpectedThrowable(UnexpectedThrowable failure) { throw new AssertionError("Client crash"); }
            };
            mc.gameSettings = new GameSettings(mc, directory);
            mc.texturePackList = new TexturePackList(mc, directory);
            mc.renderEngine = new RenderEngine(mc.texturePackList, mc.gameSettings);
            mc.fontRenderer = new FontRenderer(mc.gameSettings, "/font/default.png", mc.renderEngine);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GuiControls controls = new GuiControls(null, mc.gameSettings);
            controls.setWorldAndResolution(mc, 427, 240);
            controls.drawScreen(0, 0, 0);
            new File("build/render-check").mkdirs();
            ScreenShotHelper.saveScreenshot(new File("build/render-check"), 854, 480);
            GuiOptions options = new GuiOptions(null, mc.gameSettings);
            options.setWorldAndResolution(mc, 427, 240);
            options.drawScreen(0, 0, 0);
            boolean fov = false;
            for (Object object : options.controlList) {
                GuiButton button = (GuiButton)object;
                if (button.id == EnumOptions.FOV.ordinal()) fov = button instanceof GuiSlider;
            }
            check(fov, "FOV slider exists in Options");
            EntityLiving player = new EntityLiving(null) {
                public boolean isInsideOfMaterial(Material material) { return false; }
            };
            mc.renderViewEntity = player;
            EntityRenderer renderer = new EntityRenderer(mc);
            Method fovMethod = EntityRenderer.class.getDeclaredMethod("getFOVModifier", float.class);
            fovMethod.setAccessible(true);
            mc.gameSettings.fov = 90;
            mc.inGameHasFocus = true;
            mc.gameSettings.keyBindZoom = new KeyBinding("Test held zoom", 46) { public boolean isDown() { return true; } };
            check((Float)fovMethod.invoke(renderer, 0F) == 22.5F, "Held zoom divides current FOV");
            mc.currentScreen = controls;
            check((Float)fovMethod.invoke(renderer, 0F) == 90F, "Opening menu restores FOV");
            mc.currentScreen = null; mc.inGameHasFocus = false;
            check((Float)fovMethod.invoke(renderer, 0F) == 90F, "Losing focus restores FOV");
            mc.theWorld = new EmptyWorld();
            mc.renderGlobal = new RenderGlobal(mc, mc.renderEngine);
            player.yOffset = 1.62F; player.posX = player.prevPosX = player.posY = player.prevPosY = player.posZ = player.prevPosZ = 0;
            Method orient = EntityRenderer.class.getDeclaredMethod("orientCamera", float.class);
            orient.setAccessible(true);
            for (int perspective = 1; perspective <= 2; perspective++) {
                mc.gameSettings.thirdPersonView = perspective;
                for (float yaw : new float[]{0, 45, 180}) for (float pitch : new float[]{-30, 0, 30}) {
                    player.rotationYaw = player.prevRotationYaw = yaw;
                    player.rotationPitch = player.prevRotationPitch = pitch;
                    GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
                    orient.invoke(renderer, 0F);
                    FloatBuffer matrix = GLAllocation.createDirectFloatBuffer(16);
                    GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
                    double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
                    double x = -Math.sin(y) * Math.cos(p), v = -Math.sin(p), z = Math.cos(y) * Math.cos(p);
                    double cameraX = matrix.get(0) * x + matrix.get(4) * v + matrix.get(8) * z;
                    double cameraY = matrix.get(1) * x + matrix.get(5) * v + matrix.get(9) * z;
                    double cameraZ = matrix.get(2) * x + matrix.get(6) * v + matrix.get(10) * z;
                    check(Math.abs(cameraX) < .002 && Math.abs(cameraY) < .002, "Camera faces along player axis");
                    check(Math.abs(cameraZ - (perspective == 2 ? 1 : -1)) < .002, "Front/rear look direction");
                    check(Math.abs(matrix.get(14) + 4) < .002, "Camera remains four blocks from player");
                }
            }
            check(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error during render integration tests");
            mc.running = false;
            System.out.println("PASS: rendered RGBA, Controls, FOV slider, zoom focus, 18 front/rear camera orientations");
        } finally {
            Display.destroy();
        }
    }
}
