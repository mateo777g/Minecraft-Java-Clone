package com.minejava.ui;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import com.minejava.config.Constants;
import com.minejava.render.ChunkMeshBuilder;
import com.minejava.render.Texture;
import com.minejava.world.Block;

// Pantalla de inicio: fondo de tierra y los botones Jugar (verde) y Salir (rojo).
// Lee el ratón cada frame con glfwGetCursorPos/glfwGetMouseButton, sin tocar los callbacks de Input.
public class MenuPrincipal {

    private static final float ANCHO_BOTON = 400f;
    private static final float ALTO_BOTON = 40f;
    private static final float SEPARACION = 16f;
    private static final float TAM_BALDOSA = 64f; // Tamaño en pantalla de cada cuadro de tierra del fondo

    private final Boton jugar;
    private final Boton salir;

    private final double[] ratonX = new double[1];
    private final double[] ratonY = new double[1];
    private boolean apretadoAntes;
    private boolean clicEnJugar;
    private boolean clicEnSalir;

    public MenuPrincipal() {
        // Centrados en horizontal; empiezan a media pantalla para dejarle espacio arriba al título
        float x = (Constants.SCREEN_WIDTH - ANCHO_BOTON) / 2f;
        float y = Constants.SCREEN_HEIGHT / 2f;
        jugar = new Boton(x, y, ANCHO_BOTON, ALTO_BOTON, 0.25f, 0.55f, 0.25f);
        salir = new Boton(x, y + ALTO_BOTON + SEPARACION, ANCHO_BOTON, ALTO_BOTON, 0.6f, 0.2f, 0.2f);
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

    public void render(Texture atlas) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT, 0, -1, 1);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        dibujarFondo(atlas);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        jugar.render();
        salir.render();

        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    // La casilla de tierra del atlas repetida por toda la pantalla y oscurecida, como en Minecraft.
    // GL_REPEAT repetiría el atlas entero, así que se dibuja un cuadrado por baldosa.
    private void dibujarFondo(Texture atlas) {
        float[] uv = ChunkMeshBuilder.getUVs(Block.DIRT);
        float uMin = uv[0], uMax = uv[1], vMin = uv[2], vMax = uv[3];

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        atlas.bind();
        GL11.glColor4f(0.25f, 0.25f, 0.25f, 1f); // El color multiplica a la textura: la oscurece

        GL11.glBegin(GL11.GL_QUADS);
        for (float y = 0; y < Constants.SCREEN_HEIGHT; y += TAM_BALDOSA) {
            for (float x = 0; x < Constants.SCREEN_WIDTH; x += TAM_BALDOSA) {
                // Texture voltea la imagen al cargarla: vMax es el borde de arriba de la casilla
                GL11.glTexCoord2f(uMin, vMax); GL11.glVertex2f(x, y);
                GL11.glTexCoord2f(uMax, vMax); GL11.glVertex2f(x + TAM_BALDOSA, y);
                GL11.glTexCoord2f(uMax, vMin); GL11.glVertex2f(x + TAM_BALDOSA, y + TAM_BALDOSA);
                GL11.glTexCoord2f(uMin, vMin); GL11.glVertex2f(x, y + TAM_BALDOSA);
            }
        }
        GL11.glEnd();
    }

    public boolean clicEnJugar() {
        return clicEnJugar;
    }

    public boolean clicEnSalir() {
        return clicEnSalir;
    }
}
