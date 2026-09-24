package com.minejava.ui;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import com.minejava.config.Constants;
import com.minejava.render.Texture;
import com.minejava.world.gen.Semilla;

// Pantalla "Crear mundo", que se abre con "Un jugador". Tiene el estilo del menú principal: el fondo de
// tierra, el título, el campo "Semilla" y los botones Crear mundo y Cancelar. ESC también es Cancelar
// (lo resuelve Main). Enter es Crear mundo.
// Lee el ratón cada frame como MenuPausa. El teclado le llega de Main (escribir() y tecla()), porque
// cada ventana tiene un solo callback de teclado y es el de Main, que también atiende ESC.
public class PantallaCrearMundo {

    private static final String TITULO = "Crear mundo";
    private static final String ETIQUETA = "Semilla";
    private static final String AYUDA = "Déjala vacía para una semilla al azar";
    private static final int ESCALA_TEXTO = 2;
    private static final float GRIS = 0.63f;     // La etiqueta y la ayuda, como el texto secundario de Minecraft
    private static final int MAX_LETRAS = 32;    // Lo mismo que el campo de la semilla en Minecraft
    private static final float ANCHO = 400f;     // El del campo y el de los dos botones juntos
    private static final float ALTO_CAMPO = 40f;
    private static final float ALTO_BOTON = 40f;
    private static final float SEPARACION = 8f;  // Entre los dos botones
    // Distancias de arriba abajo
    private static final float TITULO_A_CAMPO = 80f;  // Del centro del título al borde de arriba del campo
    private static final float ESPACIO_ETIQUETA = 16f; // Del centro de "Semilla" al campo
    private static final float ESPACIO_AYUDA = 20f;    // Del campo al centro de la ayuda
    private static final float CAMPO_A_BOTONES = 56f;

    private final Texto fuente;
    private final float xCampo;
    private final float yTitulo;
    private final float yCampo;
    private final CampoTexto campo;
    private final Boton crear;
    private final Boton cancelar;

    private final double[] ratonX = new double[1];
    private final double[] ratonY = new double[1];
    private boolean apretadoAntes;
    // El botón donde se apretó el ratón: el clic solo cuenta si se suelta sobre ese mismo botón
    private Boton botonApretado;
    private boolean enterApretado;
    private boolean clicEnCrear;
    private boolean clicEnCancelar;

    // Recibe la fuente para que el campo acepte solo las letras que se pueden dibujar
    public PantallaCrearMundo(Texto fuente) {
        this.fuente = fuente;

        // El título, el campo y los botones se centran juntos en la pantalla
        float alto = TITULO_A_CAMPO + ALTO_CAMPO + CAMPO_A_BOTONES + ALTO_BOTON;
        yTitulo = (Constants.SCREEN_HEIGHT - alto) / 2f;
        xCampo = (Constants.SCREEN_WIDTH - ANCHO) / 2f;
        yCampo = yTitulo + TITULO_A_CAMPO;
        campo = new CampoTexto(fuente, xCampo, yCampo, ANCHO, ALTO_CAMPO, MAX_LETRAS);

        // Uno al lado del otro, como en Minecraft
        float yBotones = yCampo + ALTO_CAMPO + CAMPO_A_BOTONES;
        float anchoBoton = (ANCHO - SEPARACION) / 2f;
        crear = new Boton("Crear mundo", xCampo, yBotones, anchoBoton, ALTO_BOTON);
        cancelar = new Boton("Cancelar", xCampo + anchoBoton + SEPARACION, yBotones, anchoBoton, ALTO_BOTON);
    }

    // Se llama cada vez que se entra a la pantalla: el campo empieza vacío, como en Minecraft
    public void abrir() {
        campo.vaciar();

        // El clic en "Un jugador" ya se soltó, pero por si acaso: soltar el ratón al entrar no cuenta como clic
        apretadoAntes = true;
        botonApretado = null;
        enterApretado = false;
        clicEnCrear = false;
        clicEnCancelar = false;
    }

    // Un carácter escrito (glfwSetCharCallback de Main)
    public void escribir(int codigo) {
        campo.escribir(codigo);
    }

    // Una tecla (callback del teclado de Main, menos ESC): Enter crea el mundo; Borrar y Ctrl+V son del campo
    public void tecla(long window, int tecla, int accion, int mods) {
        if ((tecla == GLFW.GLFW_KEY_ENTER || tecla == GLFW.GLFW_KEY_KP_ENTER) && accion == GLFW.GLFW_PRESS) {
            enterApretado = true;
        } else {
            campo.tecla(window, tecla, accion, mods);
        }
    }

    // Hover de los botones y clics. Como en MenuPausa, el clic cuenta al SOLTAR el botón izquierdo
    // y solo si se apretó sobre el mismo botón.
    public void update(long window) {
        GLFW.glfwGetCursorPos(window, ratonX, ratonY);
        double mx = ratonX[0];
        double my = ratonY[0];

        crear.actualizar(mx, my);
        cancelar.actualizar(mx, my);

        boolean apretado = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (apretado && !apretadoAntes) {
            botonApretado = crear.contiene(mx, my) ? crear : cancelar.contiene(mx, my) ? cancelar : null;
        }
        boolean seSolto = apretadoAntes && !apretado;
        apretadoAntes = apretado;

        clicEnCrear = enterApretado || (seSolto && botonApretado == crear && crear.contiene(mx, my));
        clicEnCancelar = seSolto && botonApretado == cancelar && cancelar.contiene(mx, my);
        enterApretado = false;
    }

    public void render(Texture atlas) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT, 0, -1, 1);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        FondoTierra.dibujar(atlas);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        fuente.dibujarCentrado(TITULO, Constants.SCREEN_WIDTH / 2f, yTitulo, ESCALA_TEXTO, 1f, 1f, 1f);

        // "Semilla" arriba del campo y la ayuda abajo, pegadas a su borde izquierdo, en gris
        fuente.dibujarCentradoVertical(ETIQUETA, xCampo, yCampo - ESPACIO_ETIQUETA, ESCALA_TEXTO, GRIS, GRIS, GRIS);
        campo.render();
        fuente.dibujarCentradoVertical(AYUDA, xCampo, yCampo + ALTO_CAMPO + ESPACIO_AYUDA, ESCALA_TEXTO, GRIS, GRIS, GRIS);

        crear.render(fuente);
        cancelar.render(fuente);

        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public boolean clicEnCrear() {
        return clicEnCrear;
    }

    public boolean clicEnCancelar() {
        return clicEnCancelar;
    }

    // La semilla de lo que está escrito (ver Semilla.desdeTexto()). Si el campo está vacío sale una
    // al azar distinta cada vez, así que hay que llamarlo una sola vez, al crear el mundo.
    public long getSemilla() {
        return Semilla.desdeTexto(campo.getTexto());
    }
}
