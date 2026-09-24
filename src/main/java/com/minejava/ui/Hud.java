package com.minejava.ui;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import org.joml.Matrix4f;

import java.nio.FloatBuffer;

import com.minejava.config.Constants;
import com.minejava.render.ShaderProgram;
import com.minejava.render.Texture;

public class Hud {
    
    private static int vaoId;
    private static int vboId;
    
    public static void init() {
        vaoId = GL30.glGenVertexArrays();
        vboId = GL15.glGenBuffers();
    }

    public static void render(ShaderProgram shader, Texture texture, int selectedSlot) {
        if (vaoId == 0) init();

        // =========================================================
        // MATEMÁTICAS DE LA HOTBAR (Estilo Clásico - Al Ras)
        // =========================================================
        float slotSize = 48f;      // Tamaño de la casilla individual
        float spacing = 2f;        // Espacio interno entre casillas
        float padding = 2f;        // <-- REDUCIDO A 2 PARA QUEDAR AL RAS DEL FONDO
        int numBlocks = Constants.BLOQUES_HOTBAR.length;

        // Calculamos el tamaño total de la barra
        float innerWidth = (numBlocks * slotSize) + ((numBlocks - 1) * spacing);
        float barWidth = innerWidth + (padding * 2);
        float barHeight = slotSize + (padding * 2);

        // Posición de la barra en la pantalla (centrada abajo)
        float barX = (Constants.SCREEN_WIDTH / 2f) - (barWidth / 2f);
        float barY = Constants.SCREEN_HEIGHT - barHeight - 10f;

        // =========================================================
        // 1. RENDERIZAR INTERFAZ 2D (Fondo, Marcos y Mira)
        // =========================================================
        GL11.glDisable(GL11.GL_DEPTH_TEST); 
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D); 

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT, 0, -1, 1);
        
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        // --- A. Fondo oscuro principal ---
        GL11.glColor4f(0.15f, 0.15f, 0.15f, 0.8f); 
        GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(barX, barY);
            GL11.glVertex2f(barX + barWidth, barY);
            GL11.glVertex2f(barX + barWidth, barY + barHeight);
            GL11.glVertex2f(barX, barY + barHeight);
        GL11.glEnd();

        // --- B. Dibujar los marcos individuales de cada slot ---
        GL11.glColor4f(0.4f, 0.4f, 0.4f, 0.9f); // Gris medio para los bordes internos
        GL11.glLineWidth(2.0f);
        float startSlotX = barX + padding;
        float startSlotY = barY + padding;

        for (int i = 0; i < numBlocks; i++) {
            float currentX = startSlotX + (i * (slotSize + spacing));
            GL11.glBegin(GL11.GL_LINE_LOOP);
                GL11.glVertex2f(currentX, startSlotY);
                GL11.glVertex2f(currentX + slotSize, startSlotY);
                GL11.glVertex2f(currentX + slotSize, startSlotY + slotSize);
                GL11.glVertex2f(currentX, startSlotY + slotSize);
            GL11.glEnd();
        }

        // --- C. Dibujar el Selector (Marco destacado) ---
        float selectedX = startSlotX + (selectedSlot * (slotSize + spacing));
        float selMargin = 2f; // Abrazando perfectamente la casilla

        // SE ELIMINÓ EL RELLENO DEL SELECTOR PARA NO ALTERAR EL FONDO OSCURO

        // Borde grueso del selector con tu color pastel (#d7e8d3)
        GL11.glColor4f(0.84f, 0.91f, 0.83f, 1.0f); 
        GL11.glLineWidth(4.0f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(selectedX - selMargin, startSlotY - selMargin);
            GL11.glVertex2f(selectedX + slotSize + selMargin, startSlotY - selMargin);
            GL11.glVertex2f(selectedX + slotSize + selMargin, startSlotY + slotSize + selMargin);
            GL11.glVertex2f(selectedX - selMargin, startSlotY + slotSize + selMargin);
        GL11.glEnd();

        // --- D. Dibujar la Mira (Crosshair) ---
        float size = 7.0f; 
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.8f); 
        GL11.glLineWidth(2.0f);
        GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f((Constants.SCREEN_WIDTH / 2f) - size, Constants.SCREEN_HEIGHT / 2f);
            GL11.glVertex2f((Constants.SCREEN_WIDTH / 2f) + size, Constants.SCREEN_HEIGHT / 2f);
            GL11.glVertex2f(Constants.SCREEN_WIDTH / 2f, (Constants.SCREEN_HEIGHT / 2f) - size);
            GL11.glVertex2f(Constants.SCREEN_WIDTH / 2f, (Constants.SCREEN_HEIGHT / 2f) + size);
        GL11.glEnd();
        
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f); 
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        // =========================================================
        // 2. RENDERIZAR LOS BLOQUES 3D (Tamaño Fijo)
        // =========================================================
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT); 

        shader.bind();
        texture.bind();

        FloatBuffer matrixBuffer = MemoryUtil.memAllocFloat(16);

        Matrix4f orthoProj = new Matrix4f().ortho(0, Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT, 0, -1000f, 1000f);
        int projLoc = GL20.glGetUniformLocation(shader.getProgramId(), "projection");
        GL20.glUniformMatrix4fv(projLoc, false, orthoProj.get(matrixBuffer));

        Matrix4f identityView = new Matrix4f().identity();
        int viewLoc = GL20.glGetUniformLocation(shader.getProgramId(), "view");
        GL20.glUniformMatrix4fv(viewLoc, false, identityView.get(matrixBuffer));

        int modelLoc = GL20.glGetUniformLocation(shader.getProgramId(), "model");

        // Calculamos el centro de los slots para colocar los cubos
        float centerY = startSlotY + (slotSize / 2f);

        for (int i = 0; i < numBlocks; i++) {
            int blockId = Constants.BLOQUES_HOTBAR[i];
            
            float currentSlotX = startSlotX + (i * (slotSize + spacing));
            float centerX = currentSlotX + (slotSize / 2f);
            
            float blockScale = 26f; 

            Matrix4f modelMatrix = new Matrix4f()
                .translate(centerX, centerY, 0f)
                .rotateX((float) Math.toRadians(30)) 
                .rotateY((float) Math.toRadians(45)) 
                .scale(blockScale); 

            GL20.glUniformMatrix4fv(modelLoc, false, modelMatrix.get(matrixBuffer));

            float[] vertices = buildSingleCube(blockId);
            updateBuffer(vertices);

            GL30.glBindVertexArray(vaoId);
            GL20.glEnableVertexAttribArray(0);
            GL20.glEnableVertexAttribArray(1);

            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertices.length / 5);

            GL20.glDisableVertexAttribArray(0);
            GL20.glDisableVertexAttribArray(1);
            GL30.glBindVertexArray(0);
        }

        shader.unbind();
        GL11.glDisable(GL11.GL_BLEND);
        MemoryUtil.memFree(matrixBuffer);
    }

    private static void updateBuffer(float[] vertices) {
        FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);
        buffer.put(vertices).flip();

        GL30.glBindVertexArray(vaoId);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_DYNAMIC_DRAW);

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 5 * Float.BYTES, 0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
        MemoryUtil.memFree(buffer);
    }

    private static float[] buildSingleCube(int blockType) {
        float atlasSize = 4.0f; 
        int columnas = 4;
        int xCoord = blockType % columnas; 
        int yCoord = blockType / columnas; 
        int invertedY = (columnas - 1) - yCoord; 
        
        float uMin = (float)xCoord / atlasSize;
        float uMax = ((float)xCoord + 1.0f) / atlasSize;
        float vMin = (float)invertedY / atlasSize;
        float vMax = ((float)invertedY + 1.0f) / atlasSize;

        return new float[] {
            -0.5f,  0.5f,  0.5f,  uMin, vMin, -0.5f, -0.5f,  0.5f,  uMin, vMax,  0.5f, -0.5f,  0.5f,  uMax, vMax,
            -0.5f,  0.5f,  0.5f,  uMin, vMin,  0.5f, -0.5f,  0.5f,  uMax, vMax,  0.5f,  0.5f,  0.5f,  uMax, vMin,
             0.5f,  0.5f, -0.5f,  uMin, vMin,  0.5f, -0.5f, -0.5f,  uMin, vMax, -0.5f, -0.5f, -0.5f,  uMax, vMax,
             0.5f,  0.5f, -0.5f,  uMin, vMin, -0.5f, -0.5f, -0.5f,  uMax, vMax, -0.5f,  0.5f, -0.5f,  uMax, vMin,
            -0.5f,  0.5f, -0.5f,  uMin, vMin, -0.5f, -0.5f, -0.5f,  uMin, vMax, -0.5f, -0.5f,  0.5f,  uMax, vMax,
            -0.5f,  0.5f, -0.5f,  uMin, vMin, -0.5f, -0.5f,  0.5f,  uMax, vMax, -0.5f,  0.5f,  0.5f,  uMax, vMin,
             0.5f,  0.5f,  0.5f,  uMin, vMin,  0.5f, -0.5f,  0.5f,  uMin, vMax,  0.5f, -0.5f, -0.5f,  uMax, vMax,
             0.5f,  0.5f,  0.5f,  uMin, vMin,  0.5f, -0.5f, -0.5f,  uMax, vMax,  0.5f,  0.5f, -0.5f,  uMax, vMin,
            -0.5f,  0.5f, -0.5f,  uMin, vMin, -0.5f,  0.5f,  0.5f,  uMin, vMax,  0.5f,  0.5f,  0.5f,  uMax, vMax,
            -0.5f,  0.5f, -0.5f,  uMin, vMin,  0.5f,  0.5f,  0.5f,  uMax, vMax,  0.5f,  0.5f, -0.5f,  uMax, vMin,
            -0.5f, -0.5f,  0.5f,  uMin, vMin, -0.5f, -0.5f, -0.5f,  uMin, vMax,  0.5f, -0.5f, -0.5f,  uMax, vMax,
            -0.5f, -0.5f,  0.5f,  uMin, vMin,  0.5f, -0.5f, -0.5f,  uMax, vMax,  0.5f, -0.5f,  0.5f,  uMax, vMin
        };
    }
    
    public static void cleanup() {
        if (vaoId != 0) {
            GL30.glDeleteVertexArrays(vaoId);
            GL15.glDeleteBuffers(vboId);
        }
    }
}