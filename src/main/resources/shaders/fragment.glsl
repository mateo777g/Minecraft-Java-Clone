#version 330 core

out vec4 FragColor;
in vec2 TexCoord;
uniform sampler2D texture1;

void main() {
    // === COLORES PUROS INDEPENDIENTES ===
    if (TexCoord.x < 0.0) {
        if (TexCoord.x > -1.5) {
            // AGUA: Tu azul rey perfecto, pero con Alpha en 0.6 (Transparente estilo HUD)
            FragColor = vec4(0.0, 0.172, 0.529, 0.6);
        } else {
            // NUBES: Blanco puro y completamente SÓLIDO (Alpha = 1.0)
            FragColor = vec4(1.0, 1.0, 1.0, 1.0); 
        }
        return; 
    }

    // === TEXTURAS NORMALES DEL JUEGO ===
    vec4 texColor = texture(texture1, TexCoord);
    if(texColor.a < 0.1) {
        discard;
    }
    FragColor = texColor;
}