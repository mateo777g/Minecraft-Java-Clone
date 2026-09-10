package core;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import utils.Constants;

public class Input {

    private static double lastMouseX;
    private static double lastMouseY;
    private static boolean firstMouse = true;
    private static int selectedBlockType = 3; 

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
                    mundo.interactuarConTerreno(camara, true, selectedBlockType, jugador);  
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    // VVV CAMBIO AQUÍ: Le pasamos 'jugador' al final VVV
                    mundo.interactuarConTerreno(camara, false, selectedBlockType, jugador); 
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                    try {
                        int bloqueMirado = mundo.getBlockAtCrosshair(camara);
                        System.out.println("Pick Block -> ID detectado en el mundo: " + bloqueMirado);

                        if (bloqueMirado >= 0 && bloqueMirado <= 5) {
                            selectedBlockType = bloqueMirado; 
                            System.out.println("Hotbar sincronizada al slot: " + selectedBlockType);
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
            if (yoffset > 0) {
                selectedBlockType = (selectedBlockType - 1 + 6) % 6;
            } else if (yoffset < 0) {
                selectedBlockType = (selectedBlockType + 1) % 6;
            }
        });
    }

    // --- 4. TECLADO ---
    public static void update(long window) {
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_1) == GLFW.GLFW_PRESS) selectedBlockType = 0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_2) == GLFW.GLFW_PRESS) selectedBlockType = 1;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_3) == GLFW.GLFW_PRESS) selectedBlockType = 2;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_4) == GLFW.GLFW_PRESS) selectedBlockType = 3;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_5) == GLFW.GLFW_PRESS) selectedBlockType = 4;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_6) == GLFW.GLFW_PRESS) selectedBlockType = 5;
    }

    public static int getSelectedBlockType() {
        return selectedBlockType;
    }
}