package com.minejava.ui;

import org.lwjgl.opengl.GL11;

// Un rectángulo que se puede apretar. Todavía no tiene texto, así que se distingue por su color.
// Coordenadas en píxeles con el origen arriba a la izquierda, igual que glfwGetCursorPos y el glOrtho del menú.
public class Boton {

    private static final float BORDE = 2f;

    private final float x, y, ancho, alto;
    private final float r, g, b;
    private boolean ratonEncima;

    public Boton(float x, float y, float ancho, float alto, float r, float g, float b) {
        this.x = x;
        this.y = y;
        this.ancho = ancho;
        this.alto = alto;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public boolean contiene(double mx, double my) {
        return mx >= x && mx < x + ancho && my >= y && my < y + alto;
    }

    // Se llama cada frame con la posición del ratón para saber si hay que iluminarlo
    public void actualizar(double mx, double my) {
        ratonEncima = contiene(mx, my);
    }

    // Supone que ya está puesto el glOrtho de la pantalla y que GL_TEXTURE_2D está apagado
    public void render() {
        // Con el ratón encima: relleno más claro y borde blanco, como en Minecraft
        float aclarar = ratonEncima ? 0.35f : 0f;
        float fr = r + (1f - r) * aclarar;
        float fg = g + (1f - g) * aclarar;
        float fb = b + (1f - b) * aclarar;
        float borde = ratonEncima ? 1f : 0f;

        rect(x, y, ancho, alto, borde, borde, borde);
        rect(x + BORDE, y + BORDE, ancho - 2 * BORDE, alto - 2 * BORDE, fr, fg, fb);

        // Relieve: una franja clara arriba y una oscura abajo
        rect(x + BORDE, y + BORDE, ancho - 2 * BORDE, BORDE,
            fr + (1f - fr) * 0.4f, fg + (1f - fg) * 0.4f, fb + (1f - fb) * 0.4f);
        rect(x + BORDE, y + alto - 2 * BORDE, ancho - 2 * BORDE, BORDE, fr * 0.6f, fg * 0.6f, fb * 0.6f);
    }

    private static void rect(float x, float y, float ancho, float alto, float r, float g, float b) {
        GL11.glColor4f(r, g, b, 1f);
        GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x + ancho, y);
            GL11.glVertex2f(x + ancho, y + alto);
            GL11.glVertex2f(x, y + alto);
        GL11.glEnd();
    }
}
