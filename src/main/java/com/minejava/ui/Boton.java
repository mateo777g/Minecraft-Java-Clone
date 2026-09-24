package com.minejava.ui;

import org.lwjgl.opengl.GL11;

// Un rectángulo gris con texto que se puede apretar, como los botones de Minecraft.
// Coordenadas en píxeles con el origen arriba a la izquierda, igual que glfwGetCursorPos y el glOrtho del menú.
public class Boton {

    private static final float BORDE = 2f;
    private static final float GRIS = 0.42f;
    private static final int ESCALA_TEXTO = 2;

    private final String texto;
    private final float x, y, ancho, alto;
    private boolean ratonEncima;

    public Boton(String texto, float x, float y, float ancho, float alto) {
        this.texto = texto;
        this.x = x;
        this.y = y;
        this.ancho = ancho;
        this.alto = alto;
    }

    public boolean contiene(double mx, double my) {
        return mx >= x && mx < x + ancho && my >= y && my < y + alto;
    }

    // Se llama cada frame con la posición del ratón para saber si hay que iluminarlo
    public void actualizar(double mx, double my) {
        ratonEncima = contiene(mx, my);
    }

    // Supone que ya está puesto el glOrtho de la pantalla y que GL_TEXTURE_2D está apagado
    public void render(Texto fuente) {
        // Con el ratón encima: relleno más claro y borde blanco
        float relleno = ratonEncima ? GRIS + (1f - GRIS) * 0.35f : GRIS;
        float borde = ratonEncima ? 1f : 0f;

        rect(x, y, ancho, alto, borde);
        rect(x + BORDE, y + BORDE, ancho - 2 * BORDE, alto - 2 * BORDE, relleno);

        // Relieve: una franja clara arriba y una oscura abajo
        rect(x + BORDE, y + BORDE, ancho - 2 * BORDE, BORDE, relleno + (1f - relleno) * 0.4f);
        rect(x + BORDE, y + alto - 2 * BORDE, ancho - 2 * BORDE, BORDE, relleno * 0.6f);

        // Texto blanco, amarillo claro con el ratón encima, como en Minecraft
        float azul = ratonEncima ? 0.63f : 1f;
        fuente.dibujarCentrado(texto, x + ancho / 2f, y + alto / 2f, ESCALA_TEXTO, 1f, 1f, azul);
    }

    private static void rect(float x, float y, float ancho, float alto, float gris) {
        GL11.glColor4f(gris, gris, gris, 1f);
        GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x + ancho, y);
            GL11.glVertex2f(x + ancho, y + alto);
            GL11.glVertex2f(x, y + alto);
        GL11.glEnd();
    }
}
