package com.minejava.ui;

import org.lwjgl.opengl.GL11;

import com.minejava.config.Constants;
import com.minejava.render.Texture;

// Pantalla de "Generando mundo...": el fondo de tierra del menú con el texto en el centro.
// Main la dibuja desde que se aprieta "Un jugador" hasta que el chunk del spawn está listo.
public class PantallaGenerando {

    // Con tres puntos: "…" no es Latin-1 y la fuente lo dibujaría como "?"
    private static final String TEXTO = "Generando mundo...";
    private static final int ESCALA_TEXTO = 2;

    public static void render(Texture atlas, Texto fuente) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT, 0, -1, 1);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        FondoTierra.dibujar(atlas);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        fuente.dibujarCentrado(TEXTO, Constants.SCREEN_WIDTH / 2f, Constants.SCREEN_HEIGHT / 2f, ESCALA_TEXTO, 1f, 1f, 1f);

        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
