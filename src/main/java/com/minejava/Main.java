package com.minejava;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

import com.minejava.config.Constants;
import com.minejava.player.PlayerController;
import com.minejava.player.Camera;
import com.minejava.render.ShaderProgram;
import com.minejava.render.Texture;
import com.minejava.ui.Hud;
import com.minejava.world.Chunk;
import com.minejava.world.World;
import com.minejava.player.Input;

public class Main {

    private long window;  
    private PlayerController jugador;
    private Camera camara;
    private ShaderProgram shader;
    private Texture blockTexture;
    private World mundo;
    private final int renderDistance = 4; 
    private Matrix4f projectionMatrix;
    private Matrix4f modelMatrix; 
    private int ultimoChunkX = Integer.MAX_VALUE;
    private int ultimoChunkZ = Integer.MAX_VALUE;

    public void run() {
        System.out.println("Iniciando Minecraft en Java con LWJGL (Main Optimizado para Transparencias)...");
        init();
        loop();
        cleanup();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!GLFW.glfwInit()) throw new IllegalStateException("No se pudo inicializar GLFW");

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);

        window = GLFW.glfwCreateWindow(Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT, Constants.WINDOW_TITLE, 0, 0);
        if (window == 0) throw new RuntimeException("Error al crear la ventana del juego");

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);
        GLFW.glfwShowWindow(window);
        GL.createCapabilities();
        
        GL11.glClearColor(0.5f, 0.8f, 1.0f, 1.0f); 

        jugador = new PlayerController(Constants.PLAYER_START_POSITION);
        camara = new Camera();

        try {
            shader = new ShaderProgram();
            shader.createVertexShader(ShaderProgram.readResource("/shaders/vertex.glsl"));
            shader.createFragmentShader(ShaderProgram.readResource("/shaders/fragment.glsl"));
            shader.link();

            blockTexture = new Texture("/textures/terrain_atlas.png");
            
            // 1. Iniciamos el mundo
            mundo = new World(renderDistance);
            
            // 2. SISTEMA DE SPAWN DINÁMICO
            float spawnY = mundo.getAlturaSuperficie(0, 0); 
            jugador.setPosition(new Vector3f(0.5f, spawnY + 1.0f, 0.5f));
            
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        projectionMatrix = new Matrix4f().perspective((float) Math.toRadians(70.0f), 
            (float) Constants.SCREEN_WIDTH / Constants.SCREEN_HEIGHT, 0.1f, 1000.0f);
        modelMatrix = new Matrix4f().identity();

        // VVV CAMBIO AQUÍ: Ahora le pasamos también el 'jugador' a la inicialización de Inputs VVV
        Input.init(window, camara, mundo, jugador);
    }

    private void loop() {
        FloatBuffer matrixBuffer = MemoryUtil.memAllocFloat(16);
        
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        while (!GLFW.glfwWindowShouldClose(window)) {
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            Input.update(window);

            jugador.update(window, camara.getYaw(), mundo);
            camara.updatePosition(jugador.getPosition(), jugador.getCameraHeight());
            
            int chunkActualX = Math.floorDiv(Math.round(jugador.getPosition().x), Chunk.CHUNK_SIZE);
            int chunkActualZ = Math.floorDiv(Math.round(jugador.getPosition().z), Chunk.CHUNK_SIZE);

            if (chunkActualX != ultimoChunkX || chunkActualZ != ultimoChunkZ) {
                mundo.actualizarMundo(jugador.getPosition().x, jugador.getPosition().z);
                ultimoChunkX = chunkActualX;
                ultimoChunkZ = chunkActualZ;
            }

            mundo.procesarMallasPendientes();

            shader.bind();
            
            int projLoc = GL20.glGetUniformLocation(shader.getProgramId(), "projection");
            GL20.glUniformMatrix4fv(projLoc, false, projectionMatrix.get(matrixBuffer));

            int viewLoc = GL20.glGetUniformLocation(shader.getProgramId(), "view");
            GL20.glUniformMatrix4fv(viewLoc, false, camara.getViewMatrix().get(matrixBuffer));

            int modelLoc = GL20.glGetUniformLocation(shader.getProgramId(), "model");
            GL20.glUniformMatrix4fv(modelLoc, false, modelMatrix.get(matrixBuffer));

            blockTexture.bind();

            GL11.glDepthMask(true); 
            mundo.render(); 
            
            GL11.glDepthMask(false);
            
            GL11.glDepthMask(true); 

            shader.unbind();

            Hud.render(shader, blockTexture, Input.getSelectedSlot());

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
        }
        MemoryUtil.memFree(matrixBuffer);
    }
    
    private void cleanup() {
        shader.cleanup();
        blockTexture.cleanup();
        if (mundo != null) mundo.cleanup();
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        GLFW.glfwSetErrorCallback(null).free();
    }

    public static class Launcher {
        public static void main(String[] args) {
            new Main().run();
        }
    }
} 