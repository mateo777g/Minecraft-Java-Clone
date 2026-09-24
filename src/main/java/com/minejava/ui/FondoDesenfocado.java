package com.minejava.ui;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;

import com.minejava.config.Constants;

// El fondo de la pausa: una copia desenfocada de lo que se acaba de dibujar (el mundo con el HUD).
// Como en la pausa el mundo no se mueve, se desenfoca una sola vez al abrirla (capturar()) y después
// dibujar() solo pinta esa imagen. El desenfoque se hace en la CPU sobre una copia chica de la
// pantalla: tarda unos pocos milisegundos y no hace falta otro shader.
public class FondoDesenfocado {

    // La pantalla se achica a 1/4 (1280 × 720 → 320 × 180) y se desenfoca ahí. Al estirarla otra vez
    // con filtro lineal queda suave, sin que se noten los cuadros de 4 × 4.
    private static final int REDUCCION = 4;
    // Cuánto se desenfoca, en píxeles de la copia chica (en la pantalla es 4 veces más). Más = más borroso.
    private static final float SIGMA = 2f;

    private final int anchoPantalla = Constants.SCREEN_WIDTH;
    private final int altoPantalla = Constants.SCREEN_HEIGHT;
    private final int ancho = anchoPantalla / REDUCCION;
    private final int alto = altoPantalla / REDUCCION;
    private final float[] pesos = pesosGaussianos(SIGMA);
    // Una sola textura para todas las pausas: cada capturar() reemplaza su imagen, así no se acumulan
    private final int texturaId;

    public FondoDesenfocado() {
        texturaId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texturaId);
        // Lineal y no GL_NEAREST como los bloques, para que al estirarla no se vean los píxeles.
        // Tampoco usa mipmaps: el filtro que trae OpenGL por defecto los pide y sin ellos no se vería nada.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        // Con GL_REPEAT, el filtro lineal mezclaría cada borde con el del lado opuesto
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    // Copia lo que está dibujado (antes de glfwSwapBuffers), lo desenfoca y lo guarda en la textura
    public void capturar() {
        float[] imagen;
        ByteBuffer pantalla = MemoryUtil.memAlloc(anchoPantalla * altoPantalla * 4);
        try {
            GL11.glReadPixels(0, 0, anchoPantalla, altoPantalla, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pantalla);
            imagen = reducir(pantalla);
        } finally {
            MemoryUtil.memFree(pantalla);
        }

        // Gaussiano separable: primero cada fila y después cada columna
        float[] auxiliar = new float[imagen.length];
        pasada(imagen, auxiliar, true);
        pasada(auxiliar, imagen, false);

        ByteBuffer datos = MemoryUtil.memAlloc(ancho * alto * 4);
        for (int i = 0; i < ancho * alto; i++) {
            datos.put((byte) Math.round(imagen[i * 3]));
            datos.put((byte) Math.round(imagen[i * 3 + 1]));
            datos.put((byte) Math.round(imagen[i * 3 + 2]));
            datos.put((byte) 255);
        }
        datos.flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texturaId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, ancho, alto, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, datos);
        MemoryUtil.memFree(datos);
    }

    // Pinta la imagen desenfocada en toda la pantalla. Supone que ya está puesto el glOrtho de la pantalla.
    // Deja GL_TEXTURE_2D encendido.
    public void dibujar() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texturaId);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        GL11.glBegin(GL11.GL_QUADS);
            // glReadPixels empieza por la fila de abajo de la pantalla: v = 1 es el borde de arriba
            GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(0, 0);
            GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(anchoPantalla, 0);
            GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(anchoPantalla, altoPantalla);
            GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(0, altoPantalla);
        GL11.glEnd();
    }

    // Promedia cada cuadro de REDUCCION × REDUCCION píxeles de la pantalla.
    // Devuelve rojo, verde y azul seguidos, de 0 a 255, fila por fila empezando por abajo.
    private float[] reducir(ByteBuffer pantalla) {
        float[] imagen = new float[ancho * alto * 3];
        float n = REDUCCION * REDUCCION;
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                float r = 0, g = 0, b = 0;
                for (int dy = 0; dy < REDUCCION; dy++) {
                    int fila = (y * REDUCCION + dy) * anchoPantalla;
                    for (int dx = 0; dx < REDUCCION; dx++) {
                        int i = (fila + x * REDUCCION + dx) * 4;
                        r += pantalla.get(i) & 0xFF;
                        g += pantalla.get(i + 1) & 0xFF;
                        b += pantalla.get(i + 2) & 0xFF;
                    }
                }
                int j = (y * ancho + x) * 3;
                imagen[j] = r / n;
                imagen[j + 1] = g / n;
                imagen[j + 2] = b / n;
            }
        }
        return imagen;
    }

    // Una pasada del desenfoque, en horizontal o en vertical. En los bordes repite el último píxel,
    // así las orillas de la pantalla no se oscurecen.
    private void pasada(float[] origen, float[] destino, boolean horizontal) {
        int radio = pesos.length - 1;
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                float r = 0, g = 0, b = 0;
                for (int k = -radio; k <= radio; k++) {
                    int sx = horizontal ? Math.clamp(x + k, 0, ancho - 1) : x;
                    int sy = horizontal ? y : Math.clamp(y + k, 0, alto - 1);
                    int i = (sy * ancho + sx) * 3;
                    float peso = pesos[Math.abs(k)];
                    r += peso * origen[i];
                    g += peso * origen[i + 1];
                    b += peso * origen[i + 2];
                }
                int j = (y * ancho + x) * 3;
                destino[j] = r;
                destino[j + 1] = g;
                destino[j + 2] = b;
            }
        }
    }

    // Pesos de la campana de Gauss desde el centro (pesos[0]) hasta 3 sigmas; suman 1 contando los dos lados
    private static float[] pesosGaussianos(float sigma) {
        int radio = (int) Math.ceil(3 * sigma);
        float[] pesos = new float[radio + 1];
        float suma = 0;
        for (int i = 0; i <= radio; i++) {
            pesos[i] = (float) Math.exp(-(i * i) / (2 * sigma * sigma));
            suma += i == 0 ? pesos[i] : 2 * pesos[i];
        }
        for (int i = 0; i <= radio; i++) pesos[i] /= suma;
        return pesos;
    }

    public void cleanup() {
        GL11.glDeleteTextures(texturaId);
    }
}
