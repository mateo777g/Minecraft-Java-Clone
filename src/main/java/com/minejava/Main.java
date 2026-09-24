package com.minejava;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.joml.Matrix4f;

import com.minejava.config.Constants;
import com.minejava.render.ShaderProgram;
import com.minejava.render.Texture;
import com.minejava.ui.MenuPrincipal;
import com.minejava.ui.PantallaGenerando;
import com.minejava.ui.Texto;

public class Main {

    // Lo que dura todo el programa: ventana, shader, texturas, fuente, proyección y la pantalla actual.
    // Lo que pertenece a un mundo vive en Partida.
    private long window;  
    private ShaderProgram shader;
    private Texture blockTexture;
    private Texto fuente;
    private Matrix4f projectionMatrix;
    private EstadoJuego estado;
    private MenuPrincipal menu;
    private Partida partida;

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
        // Tamaño fijo: los botones y el HUD se ubican con SCREEN_WIDTH y SCREEN_HEIGHT
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);

        window = GLFW.glfwCreateWindow(Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT, Constants.WINDOW_TITLE, 0, 0);
        if (window == 0) throw new RuntimeException("Error al crear la ventana del juego");

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);
        GLFW.glfwShowWindow(window);

        // Cursor visible para el menú. Con los botones "pegajosos", un clic muy rápido
        // (por ejemplo un toque en el touchpad) no se pierde entre dos frames.
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetInputMode(window, GLFW.GLFW_STICKY_MOUSE_BUTTONS, GLFW.GLFW_TRUE);

        GL.createCapabilities();
        
        GL11.glClearColor(0.5f, 0.8f, 1.0f, 1.0f); 

        try {
            shader = new ShaderProgram();
            shader.createVertexShader(ShaderProgram.readResource("/shaders/vertex.glsl"));
            shader.createFragmentShader(ShaderProgram.readResource("/shaders/fragment.glsl"));
            shader.link();

            blockTexture = new Texture("/textures/terrain_atlas.png");
            fuente = new Texto();
            menu = new MenuPrincipal();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        projectionMatrix = new Matrix4f().perspective((float) Math.toRadians(70.0f), 
            (float) Constants.SCREEN_WIDTH / Constants.SCREEN_HEIGHT, 0.1f, 1000.0f);

        estado = EstadoJuego.MENU_PRINCIPAL;
    }

    // Botón "Un jugador": crea el mundo, que se empieza a generar en otros hilos,
    // y muestra "Generando mundo..." mientras tanto
    private void iniciarPartida() {
        partida = new Partida();
        estado = EstadoJuego.GENERANDO_MUNDO;
    }

    // Cuando el chunk del spawn está listo: captura el cursor, pone al jugador y activa los controles
    private void empezarAJugar() {
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        // En la partida nadie lee glfwGetMouseButton: sin esto un clic del juego quedaría
        // "pegado" y el próximo menú lo tomaría como un clic en sus botones
        GLFW.glfwSetInputMode(window, GLFW.GLFW_STICKY_MOUSE_BUTTONS, GLFW.GLFW_FALSE);
        partida.comenzar(window);
        estado = EstadoJuego.JUGANDO;
    }

    private void loop() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        while (!GLFW.glfwWindowShouldClose(window)) {
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            switch (estado) {
                case MENU_PRINCIPAL -> {
                    menu.update(window);
                    menu.render(blockTexture, fuente);
                    if (menu.clicEnJugar()) iniciarPartida();
                    else if (menu.clicEnSalir()) GLFW.glfwSetWindowShouldClose(window, true);
                }
                case GENERANDO_MUNDO -> {
                    partida.updateGenerando();
                    PantallaGenerando.render(blockTexture, fuente);
                    if (partida.estaLista()) empezarAJugar();
                }
                case JUGANDO -> {
                    partida.update(window);
                    partida.render(shader, blockTexture, projectionMatrix);
                }
            }

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
        }
    }
    
    private void cleanup() {
        if (partida != null) partida.cleanup();
        shader.cleanup();
        blockTexture.cleanup();
        fuente.cleanup();
        menu.cleanup();
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
