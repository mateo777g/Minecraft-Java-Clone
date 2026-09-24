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
// Crear mundo = new Partida(); cuando estaLista(), comenzar(window); salir = partida.cleanup(window).
public class Partida {

    private static final int RENDER_DISTANCE = 4; // 4 → 9 × 9 chunks
    // Columna donde aparece el jugador
    private static final int SPAWN_X = 0;
    private static final int SPAWN_Z = 0;

    private final PlayerController jugador;
    private final Camera camara;
    private final World mundo;
    private final Matrix4f modelMatrix = new Matrix4f().identity();
    private final FloatBuffer matrixBuffer = MemoryUtil.memAllocFloat(16);
    private int ultimoChunkX = Integer.MAX_VALUE;
    private int ultimoChunkZ = Integer.MAX_VALUE;

    // Crea el mundo, que empieza a generar sus chunks en otros hilos. El jugador todavía no tiene
    // spawn: eso lo hace comenzar(), cuando el terreno ya existe.
    public Partida() {
        jugador = new PlayerController(Constants.PLAYER_START_POSITION);
        camara = new Camera();

        mundo = new World(RENDER_DISTANCE);
    }

    // Mientras sale "Generando mundo...": sube a la GPU las mallas que ya estén terminadas,
    // así al entrar ya se ve buena parte del mundo
    public void updateGenerando() {
        mundo.procesarMallasPendientes();
    }

    // El spawn se calcula con los bloques del chunk del spawn: mientras su terreno no se genera son
    // todos piedra y el jugador aparecería en y = 200, encima de las nubes. Además se espera a que su
    // malla esté en la GPU, así al entrar ya se ve el suelo.
    public boolean estaLista() {
        return mundo.estaGenerado(SPAWN_X, SPAWN_Z) && mundo.estaListoParaRenderizar(SPAWN_X, SPAWN_Z);
    }

    // Pone al jugador encima de la superficie y activa los controles. Solo cuando estaLista().
    public void comenzar(long window) {
        float spawnY = mundo.getAlturaSuperficie(SPAWN_X, SPAWN_Z);
        jugador.setPosition(new Vector3f(SPAWN_X + 0.5f, spawnY + 1.0f, SPAWN_Z + 0.5f));

        Input.init(window, camara, mundo, jugador);
    }

    // Apaga los controles del ratón: en la pausa no mueve la cámara ni rompe o pone bloques, y la rueda
    // no cambia la hotbar. Mientras dure, Main no llama a update(): el jugador y los chunks quedan quietos.
    public void pausar(long window) {
        Input.desactivar(window);
    }

    // Vuelve a activar los controles. Input.init() pone firstMouse = true: la cámara no salta.
    public void reanudar(long window) {
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

    // Al salir al menú o cerrar el juego. Después hay que olvidarse de la partida (partida = null).
    public void cleanup(long window) {
        Input.desactivar(window);
        mundo.cleanup();
        MemoryUtil.memFree(matrixBuffer);
    }
}
