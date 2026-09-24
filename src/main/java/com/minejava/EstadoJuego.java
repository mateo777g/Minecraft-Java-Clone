package com.minejava;

// Qué pantalla dibuja Main en cada frame
public enum EstadoJuego {
    MENU_PRINCIPAL,
    CREAR_MUNDO,     // Campo "Semilla" con los botones Crear mundo y Cancelar
    GENERANDO_MUNDO, // "Generando mundo..." hasta que el chunk del spawn esté listo
    JUGANDO,
    PAUSA            // El mundo quieto y desenfocado detrás de "Volver al juego" y "Salir al menú"
}
