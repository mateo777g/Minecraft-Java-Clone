package com.minejava.ui;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import com.minejava.config.Constants;
import com.minejava.render.Texture;

// Pantalla de inicio: fondo de tierra, el título y los botones Un jugador y Salir.
// Lee el ratón cada frame con glfwGetCursorPos/glfwGetMouseButton, sin tocar los callbacks de Input.
public class MenuPrincipal {

    private static final float ANCHO_BOTON = 400f;
    private static final float ALTO_BOTON = 40f;
    private static final float SEPARACION = 16f;
    private static final float ESPACIO_TITULO = 56f; // Entre el título y el primer botón

    private final Texture titulo;
    private final float yTitulo;
    private final Boton jugar;
    private final Boton salir;

    private final double[] ratonX = new double[1];
    private final double[] ratonY = new double[1];
    private boolean apretadoAntes;
    private boolean clicEnJugar;
    private boolean clicEnSalir;

    public MenuPrincipal() throws Exception {
        // Imagen hecha con herramientas/GenerarTitulo.java (tipografía MINECRAFT PE)
        titulo = new Texture("/textures/titulo.png");

        // Centrados en horizontal; empiezan a media pantalla para dejarle espacio arriba al título
        float x = (Constants.SCREEN_WIDTH - ANCHO_BOTON) / 2f;
        float y = Constants.SCREEN_HEIGHT / 2f;
        jugar = new Boton("Un jugador", x, y, ANCHO_BOTON, ALTO_BOTON);
        salir = new Boton("Salir", x, y + ALTO_BOTON + SEPARACION, ANCHO_BOTON, ALTO_BOTON);
        yTitulo = y - ESPACIO_TITULO - titulo.getAlto();
    }

    // Hover de los botones y clics. El clic cuenta al SOLTAR el botón izquierdo,
    // así mantenerlo apretado varios frames no cuenta como varios clics.
    public void update(long window) {
        GLFW.glfwGetCursorPos(window, ratonX, ratonY);
        double mx = ratonX[0];
        double my = ratonY[0];

        jugar.actualizar(mx, my);
        salir.actualizar(mx, my);

        boolean apretado = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean seSolto = apretadoAntes && !apretado;
        apretadoAntes = apretado;

        clicEnJugar = seSolto && jugar.contiene(mx, my);
        clicEnSalir = seSolto && salir.contiene(mx, my);
    }

    public void render(Texture atlas, Texto fuente) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT, 0, -1, 1);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        FondoTierra.dibujar(atlas);
        dibujarTitulo();

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        jugar.render(fuente);
        salir.render(fuente);

        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    // A su tamaño real (1 píxel de la imagen = 1 píxel de la pantalla), centrado arriba de los botones.
    // Lo de afuera de las letras es transparente, así que hace falta el blending.
    private void dibujarTitulo() {
        float x = Math.round((Constants.SCREEN_WIDTH - titulo.getAncho()) / 2f);
        float y = Math.round(yTitulo);
        float ancho = titulo.getAncho();
        float alto = titulo.getAlto();

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        titulo.bind();
        GL11.glColor4f(1f, 1f, 1f, 1f);

        GL11.glBegin(GL11.GL_QUADS);
            // Texture voltea la imagen al cargarla: v = 1 es el borde de arriba
            GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(x + ancho, y);
            GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(x + ancho, y + alto);
            GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(x, y + alto);
        GL11.glEnd();
    }

    public boolean clicEnJugar() {
        return clicEnJugar;
    }

    public boolean clicEnSalir() {
        return clicEnSalir;
    }

    public void cleanup() {
        titulo.cleanup();
    }
}
