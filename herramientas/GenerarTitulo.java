import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import javax.imageio.ImageIO;

// Dibuja el título del menú con la tipografía MINECRAFT PE y lo guarda en src/main/resources/textures/titulo.png.
// Se corre desde la carpeta del proyecto, sin compilar nada antes:
//     java herramientas/GenerarTitulo.java ruta/a/MINECRAFT_PE.ttf
//
// MINECRAFT PE (SpideRaY, kiddiefonts.com) es gratis solo para uso personal y tiene todos los derechos reservados,
// así que el .ttf no se sube al repo: solo la imagen que sale de aquí.
// Sus letras son solo el contorno; el interior se rellena con la piedra del atlas, como el logo de Minecraft.
public class GenerarTitulo {

    private static final String[] RENGLONES = { "MINECRAFT", "JAVA CLONE" };
    private static final float[] TAMANOS = { 80f, 40f };   // En píxeles
    private static final int SEPARACION = 12;              // Entre un renglón y el siguiente
    private static final int MARGEN = 2;                   // Borde transparente: el relleno necesita "rodear" las letras
    private static final int TAM_PIXEL_PIEDRA = 3;         // Cada píxel de la piedra ocupa 3 × 3 en la imagen

    private static final File ATLAS = new File("src/main/resources/textures/terrain_atlas.png");
    private static final File SALIDA = new File("src/main/resources/textures/titulo.png");

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Uso: java herramientas/GenerarTitulo.java ruta/a/MINECRAFT_PE.ttf");
            System.exit(1);
        }
        Font tipografia = Font.createFont(Font.TRUETYPE_FONT, new File(args[0]));

        BufferedImage[] renglones = new BufferedImage[RENGLONES.length];
        int ancho = 0, alto = SEPARACION * (RENGLONES.length - 1);
        for (int i = 0; i < RENGLONES.length; i++) {
            renglones[i] = dibujarRenglon(RENGLONES[i], tipografia.deriveFont(TAMANOS[i]));
            ancho = Math.max(ancho, renglones[i].getWidth());
            alto += renglones[i].getHeight();
        }

        // Los renglones uno debajo del otro, centrados
        BufferedImage titulo = new BufferedImage(ancho + 2 * MARGEN, alto + 2 * MARGEN, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = titulo.createGraphics();
        int y = MARGEN;
        for (BufferedImage renglon : renglones) {
            g.drawImage(renglon, MARGEN + (ancho - renglon.getWidth()) / 2, y, null);
            y += renglon.getHeight() + SEPARACION;
        }
        g.dispose();

        rellenarConPiedra(titulo);
        ImageIO.write(titulo, "png", SALIDA);
        System.out.println(SALIDA + ": " + titulo.getWidth() + " x " + titulo.getHeight());
    }

    // El texto en blanco y sin suavizado (para que se vea pixelado), recortado justo a lo que ocupa
    private static BufferedImage dibujarRenglon(String texto, Font fuente) {
        BufferedImage medir = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        FontMetrics metricas = medir.createGraphics().getFontMetrics(fuente);
        int ancho = metricas.stringWidth(texto) + 2 * metricas.getHeight();
        int alto = 2 * metricas.getHeight();

        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setFont(fuente);
        g.setColor(java.awt.Color.WHITE);
        g.drawString(texto, metricas.getHeight(), metricas.getHeight());
        g.dispose();

        int minX = ancho, minY = alto, maxX = -1, maxY = -1;
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                if (opaco(imagen, x, y)) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return imagen.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    // Lo transparente que se alcanza desde el borde de la imagen es "afuera"; lo transparente que queda
    // encerrado por el contorno es el interior de las letras y se pinta con la casilla de piedra del atlas
    private static void rellenarConPiedra(BufferedImage imagen) throws Exception {
        int ancho = imagen.getWidth(), alto = imagen.getHeight();
        boolean[] afuera = new boolean[ancho * alto];
        ArrayDeque<int[]> pendientes = new ArrayDeque<>();
        for (int x = 0; x < ancho; x++) {
            pendientes.add(new int[] { x, 0 });
            pendientes.add(new int[] { x, alto - 1 });
        }
        for (int y = 0; y < alto; y++) {
            pendientes.add(new int[] { 0, y });
            pendientes.add(new int[] { ancho - 1, y });
        }
        while (!pendientes.isEmpty()) {
            int[] p = pendientes.poll();
            int x = p[0], y = p[1];
            if (x < 0 || y < 0 || x >= ancho || y >= alto) continue;
            if (afuera[y * ancho + x] || opaco(imagen, x, y)) continue;
            afuera[y * ancho + x] = true;
            pendientes.add(new int[] { x + 1, y });
            pendientes.add(new int[] { x - 1, y });
            pendientes.add(new int[] { x, y + 1 });
            pendientes.add(new int[] { x, y - 1 });
        }

        // La piedra es la casilla 0 del atlas (arriba a la izquierda), reducida a 16 × 16 como las de Minecraft
        BufferedImage atlas = ImageIO.read(ATLAS);
        int casillaAncho = atlas.getWidth() / 4, casillaAlto = atlas.getHeight() / 4;
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                if (afuera[y * ancho + x] || opaco(imagen, x, y)) continue;
                int px = (x / TAM_PIXEL_PIEDRA) % 16;
                int py = (y / TAM_PIXEL_PIEDRA) % 16;
                int color = atlas.getRGB(px * casillaAncho / 16 + casillaAncho / 32, py * casillaAlto / 16 + casillaAlto / 32);
                imagen.setRGB(x, y, 0xFF000000 | color);
            }
        }
    }

    private static boolean opaco(BufferedImage imagen, int x, int y) {
        return (imagen.getRGB(x, y) >>> 24) != 0;
    }
}
