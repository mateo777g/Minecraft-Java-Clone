package com.minejava;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.joml.Matrix4f;

import com.minejava.config.Constants;
import com.minejava.debug.MedidorRendimiento;
import com.minejava.debug.MedidorRendimiento.Parte;
import com.minejava.render.ShaderProgram;
import com.minejava.render.Texture;
import com.minejava.ui.Hud;
import com.minejava.ui.MenuPausa;
import com.minejava.ui.MenuPrincipal;
import com.minejava.ui.PantallaCrearMundo;
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
    private PantallaCrearMundo crearMundo;
    private MenuPausa menuPausa;
    private Partida partida;
    // Lo pone el callback del teclado cuando se aprieta ESC; el ciclo lo lee una vez por frame
    private boolean escApretado;

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

        // ESC abre y cierra la pausa. El callback avisa una sola vez por cada vez que se aprieta: si se
        // mantiene apretada llega como GLFW_REPEAT y no cuenta, así la pausa no se abre y cierra sola.
        // Tampoco se pierde un toque muy rápido, que leyendo glfwGetKey cada frame sí se podría perder.
        // En "Crear mundo", ESC es Cancelar (lo resuelve loop()) y las demás teclas van al campo de la semilla.
        GLFW.glfwSetKeyCallback(window, (ventana, tecla, scancode, accion, mods) -> {
            if (tecla == GLFW.GLFW_KEY_ESCAPE) {
                if (accion == GLFW.GLFW_PRESS) escApretado = true;
            } else if (estado == EstadoJuego.CREAR_MUNDO) {
                crearMundo.tecla(ventana, tecla, accion, mods);
            }
        });
        // Las letras que se escriben, ya con mayúsculas, tildes y ñ según el teclado: solo las usa el campo de la semilla
        GLFW.glfwSetCharCallback(window, (ventana, codigo) -> {
            if (estado == EstadoJuego.CREAR_MUNDO) crearMundo.escribir(codigo);
        });

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
            crearMundo = new PantallaCrearMundo(fuente);
            menuPausa = new MenuPausa();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        projectionMatrix = new Matrix4f().perspective((float) Math.toRadians(70.0f), 
            (float) Constants.SCREEN_WIDTH / Constants.SCREEN_HEIGHT, 0.1f, 1000.0f);

        estado = EstadoJuego.MENU_PRINCIPAL;
    }

    // Botón "Un jugador": la pantalla para elegir la semilla, con el campo vacío
    private void abrirCrearMundo() {
        crearMundo.abrir();
        estado = EstadoJuego.CREAR_MUNDO;
    }

    // Botón "Crear mundo": crea el mundo con esa semilla, que se empieza a generar en otros hilos,
    // y muestra "Generando mundo..." mientras tanto
    private void iniciarPartida(long semilla) {
        partida = new Partida(semilla);
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

    // ESC en la partida. El mundo se dibuja una vez más, sin moverlo, para copiarlo y desenfocarlo:
    // ese es el fondo de la pausa mientras dure (el HUD queda adentro de la imagen, detrás del desenfoque).
    private void pausar() {
        partida.pausar(window);

        // Cursor libre en el centro, como en Minecraft, y botones pegajosos otra vez para el menú de pausa.
        // Va antes de copiar la pantalla: si el ratón se mueve mientras tanto, no vuelve al centro.
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursorPos(window, Constants.SCREEN_WIDTH / 2.0, Constants.SCREEN_HEIGHT / 2.0);
        GLFW.glfwSetInputMode(window, GLFW.GLFW_STICKY_MOUSE_BUTTONS, GLFW.GLFW_TRUE);

        partida.render(shader, blockTexture, projectionMatrix);
        menuPausa.abrir(partida.getSemilla());
        estado = EstadoJuego.PAUSA;
    }

    // ESC otra vez o "Volver al juego": igual que empezarAJugar(), pero con el jugador donde estaba
    private void reanudar() {
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        GLFW.glfwSetInputMode(window, GLFW.GLFW_STICKY_MOUSE_BUTTONS, GLFW.GLFW_FALSE);
        partida.reanudar(window);
        estado = EstadoJuego.JUGANDO;
    }

    // "Salir al menú": libera el mundo (chunks en la GPU e hilos) y los callbacks de Input.
    // El cursor ya está libre y los botones pegajosos activados desde la pausa.
    private void salirAlMenu() {
        partida.cleanup(window);
        partida = null;
        estado = EstadoJuego.MENU_PRINCIPAL;
    }

    private void loop() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        while (!GLFW.glfwWindowShouldClose(window)) {
            MedidorRendimiento.empezarFrame();
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            // Se revisa antes del switch para que la pantalla nueva ya se dibuje en este frame.
            // En "Crear mundo" ESC es Cancelar. En el menú y en "Generando mundo..." no hace nada.
            boolean esc = escApretado;
            escApretado = false;
            if (esc && estado == EstadoJuego.JUGANDO) pausar();
            else if (esc && estado == EstadoJuego.PAUSA) reanudar();
            else if (esc && estado == EstadoJuego.CREAR_MUNDO) estado = EstadoJuego.MENU_PRINCIPAL;

            switch (estado) {
                case MENU_PRINCIPAL -> {
                    menu.update(window);
                    menu.render(blockTexture, fuente);
                    if (menu.clicEnJugar()) abrirCrearMundo();
                    else if (menu.clicEnSalir()) GLFW.glfwSetWindowShouldClose(window, true);
                }
                case CREAR_MUNDO -> {
                    crearMundo.update(window);
                    crearMundo.render(blockTexture);
                    if (crearMundo.clicEnCrear()) iniciarPartida(crearMundo.getSemilla());
                    else if (crearMundo.clicEnCancelar()) estado = EstadoJuego.MENU_PRINCIPAL;
                }
                case GENERANDO_MUNDO -> {
                    partida.updateGenerando();
                    PantallaGenerando.render(blockTexture, fuente);
                    if (partida.estaLista()) empezarAJugar();
                }
                case JUGANDO -> {
                    MedidorRendimiento.marcar(Parte.LIMPIAR);
                    partida.update(window);
                    partida.render(shader, blockTexture, projectionMatrix);
                    MedidorRendimiento.marcar(Parte.RENDER);
                }
                case PAUSA -> {
                    // Sin partida.update(): el jugador no se mueve y los chunks no se actualizan
                    menuPausa.update(window);
                    menuPausa.render(fuente);
                    if (menuPausa.clicEnVolver()) reanudar();
                    else if (menuPausa.clicEnSalir()) salirAlMenu();
                }
            }

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
            // Solo cuenta si fue un frame de la partida (JUGANDO); el swap es lo que queda desde la última marca
            MedidorRendimiento.terminarFrame();
        }
    }
    
    private void cleanup() {
        if (partida != null) partida.cleanup(window);
        shader.cleanup();
        blockTexture.cleanup();
        fuente.cleanup();
        menu.cleanup();
        menuPausa.cleanup();
        Hud.cleanup();
        Callbacks.glfwFreeCallbacks(window); // El del teclado y el de las letras (los de Input ya los liberó partida.cleanup())
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
