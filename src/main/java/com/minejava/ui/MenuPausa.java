package com.minejava.ui;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import com.minejava.config.Constants;

// Pantalla de pausa, como la de Minecraft: el mundo quieto y desenfocado con una capa oscura encima,
// el título "Juego en pausa" y los botones Volver al juego y Salir al menú, centrados.
// Lee el ratón cada frame igual que MenuPrincipal; los callbacks de Input están apagados en la pausa.
public class MenuPausa {

    private static final String TITULO = "Juego en pausa";
    private static final int ESCALA_TITULO = 2;
    private static final float ANCHO_BOTON = 400f;
    private static final float ALTO_BOTON = 40f;
    private static final float SEPARACION = 16f;
    private static final float ESPACIO_TITULO = 48f; // Del centro del título al primer botón
    private static final float OSCURIDAD = 0.5f;     // Opacidad de la capa negra encima del mundo

    private final FondoDesenfocado fondo = new FondoDesenfocado();
    private final float yTitulo;
    private final Boton volver;
    private final Boton salir;

    private final double[] ratonX = new double[1];
    private final double[] ratonY = new double[1];
    private boolean apretadoAntes;
    // El botón donde se apretó el ratón: el clic solo cuenta si se suelta sobre ese mismo botón
    private Boton botonApretado;
    private boolean clicEnVolver;
    private boolean clicEnSalir;

    public MenuPausa() {
        // El título y los dos botones se centran juntos en la pantalla
        float alto = ESPACIO_TITULO + ALTO_BOTON + SEPARACION + ALTO_BOTON;
        yTitulo = (Constants.SCREEN_HEIGHT - alto) / 2f;

        float x = (Constants.SCREEN_WIDTH - ANCHO_BOTON) / 2f;
        float y = yTitulo + ESPACIO_TITULO;
        volver = new Boton("Volver al juego", x, y, ANCHO_BOTON, ALTO_BOTON);
        salir = new Boton("Salir al menú", x, y + ALTO_BOTON + SEPARACION, ANCHO_BOTON, ALTO_BOTON);
    }

    // Se llama al entrar a la pausa, justo después de dibujar el mundo y antes de glfwSwapBuffers:
    // copia lo que se ve y lo desenfoca una sola vez.
    public void abrir() {
        fondo.capturar();

        // Si el botón del ratón venía apretado desde la partida, soltarlo no cuenta como clic
        apretadoAntes = true;
        botonApretado = null;
        clicEnVolver = false;
        clicEnSalir = false;
    }

    // Hover de los botones y clics. Como en MenuPrincipal, el clic cuenta al SOLTAR el botón izquierdo.
    public void update(long window) {
        GLFW.glfwGetCursorPos(window, ratonX, ratonY);
        double mx = ratonX[0];
        double my = ratonY[0];

        volver.actualizar(mx, my);
        salir.actualizar(mx, my);

        boolean apretado = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (apretado && !apretadoAntes) {
            botonApretado = volver.contiene(mx, my) ? volver : salir.contiene(mx, my) ? salir : null;
        }
        boolean seSolto = apretadoAntes && !apretado;
        apretadoAntes = apretado;

        clicEnVolver = seSolto && botonApretado == volver && volver.contiene(mx, my);
        clicEnSalir = seSolto && botonApretado == salir && salir.contiene(mx, my);
    }

    public void render(Texto fuente) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT, 0, -1, 1);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        // El mundo desenfocado, con el HUD adentro de la imagen: queda detrás del desenfoque
        fondo.dibujar();

        // Capa oscura, para que el título y los botones se lean bien
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(0f, 0f, 0f, OSCURIDAD);
        GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(0, 0);
            GL11.glVertex2f(Constants.SCREEN_WIDTH, 0);
            GL11.glVertex2f(Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT);
            GL11.glVertex2f(0, Constants.SCREEN_HEIGHT);
        GL11.glEnd();

        fuente.dibujarCentrado(TITULO, Constants.SCREEN_WIDTH / 2f, yTitulo, ESCALA_TITULO, 1f, 1f, 1f);
        volver.render(fuente);
        salir.render(fuente);

        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public boolean clicEnVolver() {
        return clicEnVolver;
    }

    public boolean clicEnSalir() {
        return clicEnSalir;
    }

    public void cleanup() {
        fondo.cleanup();
    }
}
