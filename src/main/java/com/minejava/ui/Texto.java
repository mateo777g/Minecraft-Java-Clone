package com.minejava.ui;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

import org.lwjgl.opengl.GL11;

import com.minejava.render.Texture;

// Dibuja texto con la fuente de píxeles textures/fuente.png (se genera con herramientas/GenerarFuente.java).
// La imagen tiene 16 × 16 casillas de 8 × 12 píxeles, una por carácter en el orden Latin-1: la casilla
// de un carácter es su código (columna = código % 16, fila = código / 16), así que alcanza para á, ñ, ¿ y ¡.
// Coordenadas en píxeles con el origen arriba a la izquierda: supone que ya está puesto el glOrtho de la pantalla.
public class Texto {

    private static final String RUTA = "/textures/fuente.png";
    private static final int CASILLAS = 16;
    private static final int ANCHO_CASILLA = 8;
    private static final int ALTO_CASILLA = 12;
    // Las mayúsculas ocupan las filas 3 a 9 de la casilla; se usa para centrar el texto en vertical
    private static final int FILA_MAYUSCULAS = 3;
    private static final int ALTO_MAYUSCULAS = 7;
    private static final int ANCHO_ESPACIO = 3;

    private final Texture textura;
    // Columnas que ocupa cada carácter. Al dibujar se deja una columna libre entre letra y letra.
    private final int[] anchos = new int[CASILLAS * CASILLAS];

    public Texto() throws Exception {
        textura = new Texture(RUTA);
        medirAnchos();
    }

    // Como en Minecraft, cada letra mide hasta su última columna pintada: así la "i" ocupa menos que la "m".
    // Texture ya no guarda los píxeles después de subirlos a la GPU, así que la imagen se lee otra vez.
    private void medirAnchos() throws Exception {
        BufferedImage imagen;
        try (InputStream in = Texto.class.getResourceAsStream(RUTA)) {
            if (in == null) throw new Exception("No se encontró el recurso: " + RUTA);
            imagen = ImageIO.read(in);
        }
        if (imagen.getWidth() != CASILLAS * ANCHO_CASILLA || imagen.getHeight() != CASILLAS * ALTO_CASILLA) {
            throw new Exception(RUTA + " debe medir " + CASILLAS * ANCHO_CASILLA + " × " + CASILLAS * ALTO_CASILLA + " píxeles");
        }

        for (int c = 0; c < anchos.length; c++) {
            int x0 = (c % CASILLAS) * ANCHO_CASILLA;
            int y0 = (c / CASILLAS) * ALTO_CASILLA;
            for (int x = 0; x < ANCHO_CASILLA; x++) {
                for (int y = 0; y < ALTO_CASILLA; y++) {
                    if ((imagen.getRGB(x0 + x, y0 + y) >>> 24) != 0) anchos[c] = x + 1;
                }
            }
        }
        anchos[' '] = ANCHO_ESPACIO;
    }

    // Texto blanco con la esquina de arriba a la izquierda en (x, y).
    // escala = píxeles de pantalla por cada píxel de la fuente; es entera para que las letras se vean nítidas.
    public void dibujar(String texto, float x, float y, int escala) {
        dibujar(texto, x, y, escala, 1f, 1f, 1f);
    }

    public void dibujar(String texto, float x, float y, int escala, float r, float g, float b) {
        // En medio píxel algunas columnas de la fuente saldrían más anchas que otras
        int px = Math.round(x);
        int py = Math.round(y);

        // Guarda qué estaba activado para dejarlo igual al final (el HUD y el mundo apagan el blending)
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        textura.bind();

        // Sombra como la de Minecraft: el mismo texto un píxel de la fuente más abajo y a la derecha, cuatro veces más oscuro
        GL11.glColor4f(r * 0.25f, g * 0.25f, b * 0.25f, 1f);
        dibujarLetras(texto, px + escala, py + escala, escala);
        GL11.glColor4f(r, g, b, 1f);
        dibujarLetras(texto, px, py, escala);

        GL11.glPopAttrib();
    }

    // Centrado en (cx, cy): en horizontal el texto entero y en vertical la altura de las mayúsculas
    public void dibujarCentrado(String texto, float cx, float cy, int escala, float r, float g, float b) {
        dibujarCentradoVertical(texto, cx - ancho(texto, escala) / 2f, cy, escala, r, g, b);
    }

    // Empieza en x y, como dibujarCentrado(), centra la altura de las mayúsculas en cy
    public void dibujarCentradoVertical(String texto, float x, float cy, int escala, float r, float g, float b) {
        float y = cy - (FILA_MAYUSCULAS + ALTO_MAYUSCULAS / 2f) * escala;
        dibujar(texto, x, y, escala, r, g, b);
    }

    // Ancho en pantalla, sin contar la sombra
    public int ancho(String texto, int escala) {
        int ancho = 0;
        for (int i = 0; i < texto.length(); i++) {
            ancho += anchos[casilla(texto.charAt(i))] + 1;
        }
        return Math.max(0, ancho - 1) * escala; // Después de la última letra no va la columna libre
    }

    private void dibujarLetras(String texto, int x, int y, int escala) {
        int ancho = ANCHO_CASILLA * escala;
        int alto = ALTO_CASILLA * escala;

        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < texto.length(); i++) {
            int c = casilla(texto.charAt(i));

            // Mismo cálculo que ChunkMeshBuilder.getUVs(): Texture voltea la imagen al cargarla, así que la fila se invierte
            int columna = c % CASILLAS;
            int filaInvertida = (CASILLAS - 1) - c / CASILLAS;
            float uMin = (float) columna / CASILLAS;
            float uMax = (columna + 1f) / CASILLAS;
            float vMin = (float) filaInvertida / CASILLAS;
            float vMax = (filaInvertida + 1f) / CASILLAS;

            // vMax es el borde de arriba de la casilla
            GL11.glTexCoord2f(uMin, vMax); GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(uMax, vMax); GL11.glVertex2f(x + ancho, y);
            GL11.glTexCoord2f(uMax, vMin); GL11.glVertex2f(x + ancho, y + alto);
            GL11.glTexCoord2f(uMin, vMin); GL11.glVertex2f(x, y + alto);

            x += (anchos[c] + 1) * escala;
        }
        GL11.glEnd();
    }

    // Si el carácter está en la fuente. Los demás se dibujarían como '?'.
    public boolean tieneLetra(char c) {
        return c < anchos.length && anchos[c] > 0;
    }

    // Los caracteres que no están en la fuente (casilla vacía o fuera de Latin-1) se dibujan como '?'
    private int casilla(char c) {
        if (tieneLetra(c)) return c;
        return '?';
    }

    public void cleanup() {
        textura.cleanup();
    }
}
