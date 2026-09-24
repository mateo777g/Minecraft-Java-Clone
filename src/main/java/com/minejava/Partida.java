package com.minejava;

import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

import com.minejava.config.Constants;
import com.minejava.player.Camera;
import com.minejava.player.Input;
import com.minejava.player.PlayerController;
import com.minejava.render.ShaderProgram;
import com.minejava.render.Texture;
import com.minejava.ui.Hud;
import com.minejava.world.Chunk;
import com.minejava.world.World;

// Todo lo que pertenece a UNA partida: el mundo, el jugador y la cámara.
// Crear mundo = new Partida(window); salir = partida.cleanup().
public class Partida {

    private static final int RENDER_DISTANCE = 4; // 4 → 9 × 9 chunks

    private final PlayerController jugador;
    private final Camera camara;
    private final World mundo;
    private final Matrix4f modelMatrix = new Matrix4f().identity();
    private final FloatBuffer matrixBuffer = MemoryUtil.memAllocFloat(16);
    private int ultimoChunkX = Integer.MAX_VALUE;
    private int ultimoChunkZ = Integer.MAX_VALUE;

    public Partida(long window) {
        jugador = new PlayerController(Constants.PLAYER_START_POSITION);
        camara = new Camera();

        mundo = new World(RENDER_DISTANCE);

        // Spawn: encima de la superficie en (0, 0)
        float spawnY = mundo.getAlturaSuperficie(0, 0);
        jugador.setPosition(new Vector3f(0.5f, spawnY + 1.0f, 0.5f));

        Input.init(window, camara, mundo, jugador);
    }

    // Teclado, movimiento del jugador, carga de chunks y subida de mallas a la GPU
    public void update(long window) {
        Input.update(window);

        jugador.update(window, camara.getYaw(), mundo);
        camara.updatePosition(jugador.getPosition(), jugador.getCameraHeight());

        int chunkActualX = Math.floorDiv(Math.round(jugador.getPosition().x), Chunk.CHUNK_SIZE);
        int chunkActualZ = Math.floorDiv(Math.round(jugador.getPosition().z), Chunk.CHUNK_SIZE);

        if (chunkActualX != ultimoChunkX || chunkActualZ != ultimoChunkZ) {
            mundo.actualizarMundo(jugador.getPosition().x, jugador.getPosition().z);
            ultimoChunkX = chunkActualX;
            ultimoChunkZ = chunkActualZ;
        }

        mundo.procesarMallasPendientes();
    }

    // Dibuja el mundo y encima el HUD
    public void render(ShaderProgram shader, Texture blockTexture, Matrix4f projectionMatrix) {
        shader.bind();

        int projLoc = GL20.glGetUniformLocation(shader.getProgramId(), "projection");
        GL20.glUniformMatrix4fv(projLoc, false, projectionMatrix.get(matrixBuffer));

        int viewLoc = GL20.glGetUniformLocation(shader.getProgramId(), "view");
        GL20.glUniformMatrix4fv(viewLoc, false, camara.getViewMatrix().get(matrixBuffer));

        int modelLoc = GL20.glGetUniformLocation(shader.getProgramId(), "model");
        GL20.glUniformMatrix4fv(modelLoc, false, modelMatrix.get(matrixBuffer));

        blockTexture.bind();

        mundo.render();

        shader.unbind();

        Hud.render(shader, blockTexture, Input.getSelectedSlot());
    }

    public void cleanup() {
        mundo.cleanup();
        MemoryUtil.memFree(matrixBuffer);
    }
}
