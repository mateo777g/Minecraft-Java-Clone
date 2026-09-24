package com.minejava.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class Texture {
    
    // El ID de tu imagen guardada en la tarjeta gráfica
    private int textureId;
    // Tamaño de la imagen en píxeles
    private int ancho;
    private int alto;

    public Texture(String resourcePath) throws Exception {
        // 1. Pedimos a la tarjeta gráfica que nos reserve un espacio para una textura
        textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        // 2. CONFIGURACIÓN ESTILO MINECRAFT
        // Por defecto, las tarjetas gráficas "difuminan" los píxeles cuando te acercas.
        // GL_NEAREST le dice: "¡No difumines nada! Déjalo pixelado y cuadrado".
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        
        // Si la textura es más pequeña que el cubo, le decimos que la repita
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        // 3. CARGAMOS LA IMAGEN DESDE EL CLASSPATH (funciona también dentro del .jar)
        ByteBuffer archivo = leerRecurso(resourcePath);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            // ¡MUY IMPORTANTE! OpenGL lee las imágenes de abajo hacia arriba.
            // Si no ponemos esto, tu pasto se verá de cabeza.
            STBImage.stbi_set_flip_vertically_on_load(true);

            // Decodificamos el archivo (jpeg, png, jfif, etc.)
            ByteBuffer image = STBImage.stbi_load_from_memory(archivo, width, height, channels, 4);
            if (image == null) {
                throw new Exception("Error al cargar tu textura: " + resourcePath + "\nRazón: " + STBImage.stbi_failure_reason());
            }
            ancho = width.get(0);
            alto = height.get(0);

            // 4. ENVIAMOS LA IMAGEN A LA TARJETA GRÁFICA
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width.get(), height.get(), 
                              0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, image);
            
            // Genera versiones más chiquitas de tu foto para cuando el bloque esté muy lejos (Mejora el rendimiento)
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);

            // 5. LIMPIEZA
            // La foto ya está en la memoria de video (VRAM), así que la borramos de la RAM normal para que tu PC no se trabe.
            STBImage.stbi_image_free(image);
        } finally {
            MemoryUtil.memFree(archivo);
        }
    }

    // stb necesita los bytes del archivo en memoria nativa (no en un byte[] de Java)
    private static ByteBuffer leerRecurso(String resourcePath) throws Exception {
        try (InputStream in = Texture.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new Exception("No se encontró el recurso: " + resourcePath);
            }
            byte[] bytes = in.readAllBytes();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        }
    }

    // Activar la textura antes de dibujar el cubo
    public void bind() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
    }

    public int getAncho() {
        return ancho;
    }

    public int getAlto() {
        return alto;
    }

    // Borrar la textura de la GPU cuando cerremos el juego
    public void cleanup() {
        GL11.glDeleteTextures(textureId);
    }
}
