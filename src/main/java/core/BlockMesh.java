package core;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;

public class BlockMesh {
    
    // Identificadores para la memoria de la tarjeta gráfica
    private int vaoId;
    private int vboId;
    private int vertexCount;

    public BlockMesh() {
        // Aquí construimos el cubo con matemáticas: (X, Y, Z, U, V)
        float[] vertices = {
            // --- CARA FRONTAL ---
            -0.5f,  0.5f,  0.5f,  0.0f, 0.0f, // Arriba Izquierda
            -0.5f, -0.5f,  0.5f,  0.0f, 1.0f, // Abajo Izquierda
             0.5f, -0.5f,  0.5f,  1.0f, 1.0f, // Abajo Derecha
            -0.5f,  0.5f,  0.5f,  0.0f, 0.0f, // Arriba Izquierda
             0.5f, -0.5f,  0.5f,  1.0f, 1.0f, // Abajo Derecha
             0.5f,  0.5f,  0.5f,  1.0f, 0.0f, // Arriba Derecha

            // --- CARA TRASERA ---
             0.5f,  0.5f, -0.5f,  0.0f, 0.0f,
             0.5f, -0.5f, -0.5f,  0.0f, 1.0f,
            -0.5f, -0.5f, -0.5f,  1.0f, 1.0f,
             0.5f,  0.5f, -0.5f,  0.0f, 0.0f,
            -0.5f, -0.5f, -0.5f,  1.0f, 1.0f,
            -0.5f,  0.5f, -0.5f,  1.0f, 0.0f,

            // --- CARA IZQUIERDA ---
            -0.5f,  0.5f, -0.5f,  0.0f, 0.0f,
            -0.5f, -0.5f, -0.5f,  0.0f, 1.0f,
            -0.5f, -0.5f,  0.5f,  1.0f, 1.0f,
            -0.5f,  0.5f, -0.5f,  0.0f, 0.0f,
            -0.5f, -0.5f,  0.5f,  1.0f, 1.0f,
            -0.5f,  0.5f,  0.5f,  1.0f, 0.0f,

            // --- CARA DERECHA ---
             0.5f,  0.5f,  0.5f,  0.0f, 0.0f,
             0.5f, -0.5f,  0.5f,  0.0f, 1.0f,
             0.5f, -0.5f, -0.5f,  1.0f, 1.0f,
             0.5f,  0.5f,  0.5f,  0.0f, 0.0f,
             0.5f, -0.5f, -0.5f,  1.0f, 1.0f,
             0.5f,  0.5f, -0.5f,  1.0f, 0.0f,

            // --- CARA SUPERIOR ---
            -0.5f,  0.5f, -0.5f,  0.0f, 0.0f,
            -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,
             0.5f,  0.5f,  0.5f,  1.0f, 1.0f,
            -0.5f,  0.5f, -0.5f,  0.0f, 0.0f,
             0.5f,  0.5f,  0.5f,  1.0f, 1.0f,
             0.5f,  0.5f, -0.5f,  1.0f, 0.0f,

            // --- CARA INFERIOR ---
            -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,
            -0.5f, -0.5f, -0.5f,  0.0f, 1.0f,
             0.5f, -0.5f, -0.5f,  1.0f, 1.0f,
            -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,
             0.5f, -0.5f, -0.5f,  1.0f, 1.0f,
             0.5f, -0.5f,  0.5f,  1.0f, 0.0f
        };

        this.vertexCount = vertices.length / 5; // 5 valores por cada punto

        // Convertimos el arreglo de Java a un Buffer que la GPU pueda entender rápido
        FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);
        buffer.put(vertices).flip();

        // 1. Creamos el VAO (Vertex Array Object): El contenedor principal en la GPU
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        // 2. Creamos el VBO (Vertex Buffer Object): Donde guardamos los números
        vboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

        // 3. Le explicamos a la GPU qué significa cada número:
        // Los primeros 3 números (X,Y,Z) son las posiciones
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 5 * Float.BYTES, 0);
        // Los siguientes 2 números (U,V) son las texturas
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);

        // Desconectamos para no modificarlo por accidente
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        // Liberamos la memoria RAM porque los datos ya están en la tarjeta de video
        MemoryUtil.memFree(buffer);
    }

    // Método para dibujar este cubo
    public void render() {
        GL30.glBindVertexArray(vaoId);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);

        // Dibujamos los triángulos!
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);

        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL30.glBindVertexArray(0);
    }

    // Método para limpiar la memoria cuando el juego se cierre
    public void cleanup() {
        GL20.glDisableVertexAttribArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glDeleteBuffers(vboId);
        GL30.glBindVertexArray(0);
        GL30.glDeleteVertexArrays(vaoId);
    }
}