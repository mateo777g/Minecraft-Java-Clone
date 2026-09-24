package com.minejava.ui;

import org.lwjgl.opengl.GL11;

import com.minejava.config.Constants;
import com.minejava.render.ChunkMeshBuilder;
import com.minejava.render.Texture;
import com.minejava.world.Block;

// El fondo de las pantallas de menú, como en Minecraft: la casilla de tierra del atlas repetida
// por toda la pantalla y oscurecida. Lo usan el menú principal y la pantalla de "Generando mundo...".
public class FondoTierra {

    private static final float TAM_BALDOSA = 64f; // Tamaño en pantalla de cada cuadro de tierra
    private static final float BRILLO = 0.25f;

    // Supone que ya está puesto el glOrtho de la pantalla. Deja GL_TEXTURE_2D encendido.
    // GL_REPEAT repetiría el atlas entero, así que se dibuja un cuadrado por baldosa.
    public static void dibujar(Texture atlas) {
        float[] uv = ChunkMeshBuilder.getUVs(Block.DIRT);
        float uMin = uv[0], uMax = uv[1], vMin = uv[2], vMax = uv[3];

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        atlas.bind();
        GL11.glColor4f(BRILLO, BRILLO, BRILLO, 1f); // El color multiplica a la textura: la oscurece

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
}
