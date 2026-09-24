package com.minejava;

// Qué pantalla dibuja Main en cada frame
public enum EstadoJuego {
    MENU_PRINCIPAL,
    GENERANDO_MUNDO, // "Generando mundo..." hasta que el chunk del spawn esté listo
    JUGANDO
}
