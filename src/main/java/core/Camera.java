package core;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private Vector3f position;
    private float pitch; // Rotación Arriba/Abajo
    private float yaw;   // Rotación Izquierda/Derecha

    // La Matriz de Vista es la fórmula que mueve el universo
    private Matrix4f viewMatrix;

    public Camera() {
        this.position = new Vector3f(0, 0, 0);
        this.pitch = 0.0f;
        this.yaw = 0.0f;
        this.viewMatrix = new Matrix4f();
    }

    // El jugador le dirá a la cámara dónde está, y la cámara se pondrá a la altura de sus ojos
    public void updatePosition(Vector3f playerPosition, float cameraHeight) {
        this.position.x = playerPosition.x;
        this.position.y = playerPosition.y + cameraHeight;
        this.position.z = playerPosition.z;
    }

    // Cuando muevas el ratón, sumaremos esos movimientos aquí
    public void addRotation(float dPitch, float dYaw) {
        this.pitch += dPitch;
        this.yaw += dYaw;

        // ¡Evitamos que te rompas el cuello! No puedes mirar más allá de 90 grados arriba o abajo.
        if (this.pitch > 89.0f) {
            this.pitch = 89.0f;
        } else if (this.pitch < -89.0f) {
            this.pitch = -89.0f;
        }
    }

    // Esta es la función clave que usará OpenGL para dibujar el mundo en 3D
    public Matrix4f getViewMatrix() {
        viewMatrix.identity(); // Reseteamos la fórmula
        
        // 1. Rotamos el mundo según a dónde mires
        viewMatrix.rotate((float) Math.toRadians(pitch), new Vector3f(1, 0, 0));
        viewMatrix.rotate((float) Math.toRadians(yaw), new Vector3f(0, 1, 0));
        
        // 2. Movemos el mundo en la dirección opuesta a donde estás parado
        viewMatrix.translate(-position.x, -position.y, -position.z);
        
        return viewMatrix;
    }

    // Nos permite saber hacia dónde está apuntando nuestra cabeza
    public float getYaw() {
        return yaw;
    }

    // === EL METODO QUE FALTABA: Expone la posición actual de la cámara ===
    public Vector3f getPosition() {
        return position;
    }

    // Calcula el vector de dirección 3D basándose en la rotación de la cabeza (Pitch y Yaw)
    public Vector3f getDirection() {
        Vector3f direction = new Vector3f();
        
        float yawRad = (float) Math.toRadians(this.yaw);
        float pitchRad = (float) Math.toRadians(this.pitch);

        direction.x = (float) (Math.sin(yawRad) * Math.cos(pitchRad));
        direction.y = (float) -Math.sin(pitchRad);
        direction.z = (float) (-Math.cos(yawRad) * Math.cos(pitchRad));

        return direction.normalize(); 
    }
}