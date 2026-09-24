package com.minejava.ui;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

// Un campo de texto de una línea, como los de Minecraft: fondo negro, borde blanco y un "_" que parpadea
// después de la última letra. Siempre se escribe y se borra al final.
// Las letras llegan de glfwSetCharCallback (escribir()) y Borrar y Ctrl+V del callback del teclado
// (tecla()): los dos callbacks son de Main, que se los pasa a la pantalla que tiene el campo.
// Coordenadas en píxeles con el origen arriba a la izquierda, como Boton.
public class CampoTexto {

    private static final float BORDE = 2f;
    private static final float MARGEN = 8f;          // Entre el borde y el texto
    private static final int ESCALA_TEXTO = 2;
    private static final float COLOR_TEXTO = 0.88f;  // Gris muy claro, como en Minecraft
    private static final double PARPADEO = 0.3;      // Segundos que el "_" se ve y que no se ve

    private final Texto fuente;
    private final float x, y, ancho, alto;
    private final int maxLetras;
    private final StringBuilder texto = new StringBuilder();
    // Cuándo cambió el texto por última vez: el "_" se ve siempre justo después de escribir o borrar
    private double ultimoCambio;

    public CampoTexto(Texto fuente, float x, float y, float ancho, float alto, int maxLetras) {
        this.fuente = fuente;
        this.x = x;
        this.y = y;
        this.ancho = ancho;
        this.alto = alto;
        this.maxLetras = maxLetras;
    }

    public String getTexto() {
        return texto.toString();
    }

    public void vaciar() {
        texto.setLength(0);
        ultimoCambio = GLFW.glfwGetTime();
    }

    // Un carácter de glfwSetCharCallback. Solo se aceptan los que tiene la fuente: lo que se ve en el
    // campo es exactamente lo que se usa (con una letra que se dibuja como '?' no se podría repetir).
    public void escribir(int codigo) {
        if (codigo <= Character.MAX_VALUE) agregar((char) codigo);
    }

    // Borrar (también si se mantiene apretada, que llega como GLFW_REPEAT) y Ctrl+V para pegar
    public void tecla(long window, int tecla, int accion, int mods) {
        if (accion != GLFW.GLFW_PRESS && accion != GLFW.GLFW_REPEAT) return;

        if (tecla == GLFW.GLFW_KEY_BACKSPACE && texto.length() > 0) {
            texto.setLength(texto.length() - 1);
            ultimoCambio = GLFW.glfwGetTime();
        } else if (tecla == GLFW.GLFW_KEY_V && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            String pegado = GLFW.glfwGetClipboardString(window);
            if (pegado == null) return;
            // Los saltos de línea y los caracteres que no están en la fuente se saltan
            for (int i = 0; i < pegado.length(); i++) agregar(pegado.charAt(i));
        }
    }

    private void agregar(char c) {
        if (texto.length() >= maxLetras || !fuente.tieneLetra(c)) return;
        texto.append(c);
        ultimoCambio = GLFW.glfwGetTime();
    }

    // Supone que ya está puesto el glOrtho de la pantalla y que GL_TEXTURE_2D está apagado, como Boton
    public void render() {
        rect(x, y, ancho, alto, 1f);
        rect(x + BORDE, y + BORDE, ancho - 2 * BORDE, alto - 2 * BORDE, 0f);

        // Si el texto no entra, se ve el final (donde se está escribiendo), como en Minecraft
        String cursor = "_";
        int espacioCursor = fuente.ancho(cursor, ESCALA_TEXTO) + ESCALA_TEXTO;
        float disponible = ancho - 2 * (BORDE + MARGEN) - espacioCursor;
        String visible = getTexto();
        while (fuente.ancho(visible, ESCALA_TEXTO) > disponible) visible = visible.substring(1);

        float xTexto = x + BORDE + MARGEN;
        float cy = y + alto / 2f;
        fuente.dibujarCentradoVertical(visible, xTexto, cy, ESCALA_TEXTO, COLOR_TEXTO, COLOR_TEXTO, COLOR_TEXTO);

        boolean cursorVisible = (GLFW.glfwGetTime() - ultimoCambio) % (2 * PARPADEO) < PARPADEO;
        if (cursorVisible) {
            // Después de la última letra va la misma columna libre que entre letra y letra
            float xCursor = xTexto + fuente.ancho(visible, ESCALA_TEXTO) + (visible.isEmpty() ? 0 : ESCALA_TEXTO);
            fuente.dibujarCentradoVertical(cursor, xCursor, cy, ESCALA_TEXTO, COLOR_TEXTO, COLOR_TEXTO, COLOR_TEXTO);
        }
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
