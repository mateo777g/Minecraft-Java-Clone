import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

// Convierte herramientas/fuente.txt en src/main/resources/textures/fuente.png: 16 × 16 casillas de 8 × 12 píxeles,
// una por carácter en el orden Latin-1 (la casilla de un carácter es su código). El formato está explicado en fuente.txt.
// Se corre desde la carpeta del proyecto, sin compilar nada antes:
//     java herramientas/GenerarFuente.java
public class GenerarFuente {

    private static final Path ENTRADA = Path.of("herramientas/fuente.txt");
    private static final File SALIDA = new File("src/main/resources/textures/fuente.png");
    private static final int CASILLAS = 16;
    private static final int ANCHO_CASILLA = 8;
    private static final int ALTO_CASILLA = 12;

    public static void main(String[] args) throws Exception {
        List<String> lineas = Files.readAllLines(ENTRADA, StandardCharsets.UTF_8);
        BufferedImage imagen = new BufferedImage(CASILLAS * ANCHO_CASILLA, CASILLAS * ALTO_CASILLA, BufferedImage.TYPE_INT_ARGB);
        boolean[] definido = new boolean[CASILLAS * CASILLAS];
        int total = 0;

        int i = 0;
        while (i < lineas.size()) {
            String cabecera = lineas.get(i);
            if (cabecera.isBlank() || cabecera.startsWith("//")) {
                i++;
                continue;
            }
            if (i + ALTO_CASILLA >= lineas.size()) throw error(i, "al bloque le faltan filas");
            List<String> filas = lineas.subList(i + 1, i + 1 + ALTO_CASILLA);

            // Cada glifo empieza donde está su carácter en la cabecera y termina antes del espacio que lo separa del siguiente
            for (int inicio = 0; inicio < cabecera.length(); inicio++) {
                char c = cabecera.charAt(inicio);
                if (c == ' ') continue;
                if (c >= definido.length) throw error(i, "'" + c + "' no está en Latin-1");
                if (definido[c]) throw error(i, "'" + c + "' está repetido");
                definido[c] = true;
                total++;

                int fin = inicio + 1;
                while (fin < cabecera.length() && cabecera.charAt(fin) == ' ') fin++;
                if (fin < cabecera.length()) fin--; // Sin la columna del espacio separador
                else fin = Integer.MAX_VALUE;       // El último glifo del bloque llega hasta el final de la fila

                for (int y = 0; y < ALTO_CASILLA; y++) {
                    String fila = filas.get(y);
                    for (int x = inicio; x < Math.min(fin, fila.length()); x++) {
                        if (fila.charAt(x) != '#') continue;
                        if (x - inicio >= ANCHO_CASILLA) throw error(i + 1 + y, "'" + c + "' mide más de " + ANCHO_CASILLA + " píxeles");
                        imagen.setRGB((c % CASILLAS) * ANCHO_CASILLA + x - inicio, (c / CASILLAS) * ALTO_CASILLA + y, 0xFFFFFFFF);
                    }
                }
            }
            i += 1 + ALTO_CASILLA;
        }

        ImageIO.write(imagen, "png", SALIDA);
        System.out.println(SALIDA + ": " + total + " caracteres");
    }

    private static IllegalArgumentException error(int linea, String mensaje) {
        return new IllegalArgumentException(ENTRADA + ", línea " + (linea + 1) + ": " + mensaje);
    }
}
