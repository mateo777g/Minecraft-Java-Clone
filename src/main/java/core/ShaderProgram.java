package core;

import org.lwjgl.opengl.GL20;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ShaderProgram {
    
    // El ID de nuestro "programa" en la tarjeta gráfica
    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;

    public ShaderProgram() throws Exception {
        // Le pedimos a OpenGL que cree un espacio para nuestro programa
        programId = GL20.glCreateProgram();
        if (programId == 0) {
            throw new Exception("No se pudo crear el Shader Program en la GPU.");
        }
    }

    // Lee el archivo vertex.glsl y lo compila
    public void createVertexShader(String code) throws Exception {
        vertexShaderId = createShader(code, GL20.GL_VERTEX_SHADER);
    }

    // Lee el archivo fragment.glsl y lo compila
    public void createFragmentShader(String code) throws Exception {
        fragmentShaderId = createShader(code, GL20.GL_FRAGMENT_SHADER);
    }

    // El motor interno que agarra el texto de tus archivos y lo traduce a lenguaje de GPU
    private int createShader(String shaderCode, int shaderType) throws Exception {
        int shaderId = GL20.glCreateShader(shaderType);
        if (shaderId == 0) {
            throw new Exception("Error creando el shader de tipo: " + shaderType);
        }

        GL20.glShaderSource(shaderId, shaderCode);
        GL20.glCompileShader(shaderId);

        // Si te equivocas escribiendo el código en los .glsl, Java te avisará aquí:
        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
            throw new Exception("Error compilando el shader: " + GL20.glGetShaderInfoLog(shaderId, 1024));
        }

        // Pegamos esta parte al programa principal
        GL20.glAttachShader(programId, shaderId);

        return shaderId;
    }

    // Une el Vertex y el Fragment en un solo programa listo para usarse
    public void link() throws Exception {
        GL20.glLinkProgram(programId);
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
            throw new Exception("Error enlazando el programa de shaders: " + GL20.glGetProgramInfoLog(programId, 1024));
        }

        // Una vez enlazados, ya no necesitamos las piezas individuales, liberamos memoria
        if (vertexShaderId != 0) {
            GL20.glDetachShader(programId, vertexShaderId);
        }
        if (fragmentShaderId != 0) {
            GL20.glDetachShader(programId, fragmentShaderId);
        }
    }

    // "Bind" significa ACTIVAR. Cuando vayamos a dibujar el bloque, llamaremos a esto.
    public void bind() {
        GL20.glUseProgram(programId);
    }

    // "Unbind" significa DESACTIVAR.
    public void unbind() {
        GL20.glUseProgram(0);
    }

    // Limpia la tarjeta gráfica al cerrar el juego
    public void cleanup() {
        unbind();
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
        }
    }

    // --- HERRAMIENTA EXTRA PARA LEER TEXTO ---
    // Esta pequeña función nos ayudará a leer los archivos .glsl desde el disco duro
    public static String readFile(String filePath) throws Exception {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    public int getProgramId() {
        return programId;
    }
}