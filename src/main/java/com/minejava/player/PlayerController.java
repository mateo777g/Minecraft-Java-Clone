package com.minejava.player;

import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import com.minejava.world.Block;
import com.minejava.world.World;

public class PlayerController {
    
    private Vector3f position;
    private float cameraHeight = 1.62f; 
    private float speed = 0.12f; // Ajustada para que el control se sienta más preciso

    // Optimizado para colisiones perfectas en espacios de 1x1
    private final float playerRadius = 0.28f; 
    private final float playerHeight = 1.8f; 

    public PlayerController(Vector3f startPosition) {
        this.position = new Vector3f(startPosition);
    }

    public void update(long window, float cameraYaw, World mundo) {
        float dx = 0;
        float dz = 0;
        float dy = 0;

        float yawRad = (float) Math.toRadians(cameraYaw);

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) {
            dx += (float) Math.sin(yawRad);
            dz -= (float) Math.cos(yawRad);
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) {
            dx -= (float) Math.sin(yawRad);
            dz += (float) Math.cos(yawRad);
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) {
            dx -= (float) Math.cos(yawRad);
            dz -= (float) Math.sin(yawRad);
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) {
            dx += (float) Math.cos(yawRad);
            dz += (float) Math.sin(yawRad);
        }
        
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) {
            dy += 1.0f;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) {
            dy -= 1.0f;
        }

        if (dx != 0 || dz != 0) {
            float length = (float) Math.sqrt(dx * dx + dz * dz);
            dx /= length;
            dz /= length;
        }

        float moveX = dx * speed;
        float moveY = dy * speed;
        float moveZ = dz * speed;

        // Desplazamiento por ejes separado con deslizamiento suave en paredes
        position.x += moveX;
        if (comprobarColisionGlobal(position, mundo)) position.x -= moveX; 

        position.y += moveY;
        if (comprobarColisionGlobal(position, mundo)) position.y -= moveY; 

        position.z += moveZ;
        if (comprobarColisionGlobal(position, mundo)) position.z -= moveZ; 
    }

    private boolean comprobarColisionGlobal(Vector3f pos, World mundo) {
        // Epsilon muy pequeño para evitar que el jugador se meta dentro de las mallas
        float epsilon = 0.005f; 
        
        int startX = (int) Math.floor(pos.x - playerRadius + epsilon);
        int endX = (int) Math.floor(pos.x + playerRadius - epsilon);
        int startY = (int) Math.floor(pos.y + epsilon);
        int endY = (int) Math.floor(pos.y + playerHeight - epsilon);
        int startZ = (int) Math.floor(pos.z - playerRadius + epsilon);
        int endZ = (int) Math.floor(pos.z + playerRadius - epsilon);

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    int blockId = mundo.getBlockGlobal(x, y, z);
                    
                    if (Block.isSolid(blockId)) {
                        return true; 
                    }
                }
            }
        }
        return false; 
    }

    public boolean intersectsBlock(int bx, int by, int bz) {
        float minX = position.x - playerRadius;
        float maxX = position.x + playerRadius;
        float minY = position.y;
        float maxY = position.y + playerHeight;
        float minZ = position.z - playerRadius;
        float maxZ = position.z + playerRadius;

        // Verifica la superposición exacta AABB estándar
        return (maxX > bx && minX < bx + 1) &&
               (maxY > by && minY < by + 1) &&
               (maxZ > bz && minZ < bz + 1);
    }

    public Vector3f getPosition() { return position; }
    public void setPosition(Vector3f pos) { this.position = new Vector3f(pos); } 
    public float getCameraHeight() { return cameraHeight; }
}