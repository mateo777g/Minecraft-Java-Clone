#version 330 core

// Entradas desde Java (Posición del vértice y coordenadas de la textura)
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;

// Salida hacia el Fragment Shader
out vec2 TexCoord;

// Las matrices matemáticas de la cámara
uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

void main() {
    // Multiplicamos la posición por las fórmulas matemáticas de la cámara
    gl_Position = projection * view * model * vec4(aPos, 1.0);
    // Pasamos las coordenadas de la textura tal cual
    TexCoord = aTexCoord;
}