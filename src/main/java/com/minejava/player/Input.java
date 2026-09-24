package com.minejava.player;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;

import com.minejava.config.Constants;
import com.minejava.world.World;

public class Input {

    private static double lastMouseX;
    private static double lastMouseY;
    private static boolean firstMouse = true;
    private static int selectedSlot = 3; // Casilla del pasto

    // VVV CAMBIO AQUÍ: Variable estática para recordar quién es el jugador VVV
    private static PlayerController jugador;

    // VVV CAMBIO AQUÍ: El método init ahora acepta 'PlayerController jugadorInstancia' VVV
    public static void init(long window, Camera camara, World mundo, PlayerController jugadorInstancia) {
        jugador = jugadorInstancia; // Guardamos la referencia

        // Bloquear el cursor en el centro de la pantalla
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);

        // --- 1. MOVIMIENTO DE CÁMARA ---
        GLFW.glfwSetCursorPosCallback(window, new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double xpos, double ypos) {
                if (firstMouse) {
                    lastMouseX = xpos;
                    lastMouseY = ypos;
                    firstMouse = false;
                }
                float xOffset = (float) (xpos - lastMouseX);
                float yOffset = (float) (ypos - lastMouseY); 
                lastMouseX = xpos;
                lastMouseY = ypos;

                camara.addRotation(yOffset * Constants.MOUSE_SENSITIVITY, xOffset * Constants.MOUSE_SENSITIVITY);
            }
        });

        // --- 2. CLICS DEL RATÓN (Poner, Quitar y Pick Block) ---
        GLFW.glfwSetMouseButtonCallback(window, (windowId, button, action, mods) -> {
            if (action == GLFW.GLFW_PRESS) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    // VVV CAMBIO AQUÍ: Le pasamos 'jugador' al final VVV
                    mundo.interactuarConTerreno(camara, true, getSelectedBlockType(), jugador);  
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    // VVV CAMBIO AQUÍ: Le pasamos 'jugador' al final VVV
                    mundo.interactuarConTerreno(camara, false, getSelectedBlockType(), jugador); 
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                    try {
                        int bloqueMirado = mundo.getBlockAtCrosshair(camara);
                        System.out.println("Pick Block -> ID detectado en el mundo: " + bloqueMirado);

                        int slot = buscarSlot(bloqueMirado);
                        if (slot != -1) {
                            selectedSlot = slot;
                            System.out.println("Hotbar sincronizada al slot: " + selectedSlot);
                        } else {
                            System.out.println("Bloque mirado no está asignado a la hotbar o es aire (-1)");
                        }
                    } catch (Exception e) {
                        System.err.println("¡Error al copiar bloque! Evitamos el crash de GLFW: " + e.getMessage());
                    }
                }
            }
        });

        // --- 3. RUEDA DEL RATÓN (Hotbar) ---
        GLFW.glfwSetScrollCallback(window, (windowId, xoffset, yoffset) -> {
            int numSlots = Constants.BLOQUES_HOTBAR.length;
            if (yoffset > 0) {
                selectedSlot = (selectedSlot - 1 + numSlots) % numSlots;
            } else if (yoffset < 0) {
                selectedSlot = (selectedSlot + 1) % numSlots;
            }
        });
    }

    // --- 4. TECLADO ---
    public static void update(long window) {
        // Teclas 1..9 (GLFW_KEY_1 a GLFW_KEY_9 son consecutivas)
        int numTeclas = Math.min(Constants.BLOQUES_HOTBAR.length, 9);
        for (int i = 0; i < numTeclas; i++) {
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_1 + i) == GLFW.GLFW_PRESS) selectedSlot = i;
        }
    }

    // Devuelve la casilla de la hotbar que tiene ese bloque, o -1 si no está
    private static int buscarSlot(int blockId) {
        for (int i = 0; i < Constants.BLOQUES_HOTBAR.length; i++) {
            if (Constants.BLOQUES_HOTBAR[i] == blockId) return i;
        }
        return -1;
    }

    public static int getSelectedSlot() {
        return selectedSlot;
    }

    public static int getSelectedBlockType() {
        return Constants.BLOQUES_HOTBAR[selectedSlot];
    }
}