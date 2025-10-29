import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class Main {
    // Z-буфер
    private static float[][] zBuffer;

    public static void main(String[] args) throws IOException {
        process3DModel();
    }

    // Основная обработка 3D модели
    public static void process3DModel() throws IOException {
        int imgWidth = 1000, imgHeight = 1000;
        BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);

        zBuffer = new float[imgWidth][imgHeight];
        initializeZBuffer();

        ArrayList<float[]> vertices = new ArrayList<>();
        ArrayList<int[]> faces = new ArrayList<>();

        BufferedReader br = new BufferedReader(new FileReader("model_1.obj"));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");

            switch (parts[0]) {
                case "v": // вершина
                    float x = Float.parseFloat(parts[1]);
                    float y = Float.parseFloat(parts[2]);
                    float z = Float.parseFloat(parts[3]);
                    vertices.add(new float[]{x, y, z});
                    break;
                case "f": // грань
                    int[] face = new int[parts.length - 1];
                    for (int i = 1; i < parts.length; i++) {
                        String[] sub = parts[i].split("/");
                        face[i - 1] = Integer.parseInt(sub[0]) - 1;
                    }
                    faces.add(face);
                    break;
            }
        }
        br.close();

        // Параметры проекции
        float scale = 5000f;
        float offset = 500f;

        for (int[] face : faces) {
            if (face.length < 3) continue;

            // Получаем вершины треугольника
            float[] v0 = vertices.get(face[0]);
            float[] v1 = vertices.get(face[1]);
            float[] v2 = vertices.get(face[2]);

            // Преобразуем в экранные координаты
            float x0 = v0[0] * scale + offset;
            float y0 = v0[1] * scale + offset;
            float x1 = v1[0] * scale + offset;
            float y1 = v1[1] * scale + offset;
            float x2 = v2[0] * scale + offset;
            float y2 = v2[1] * scale + offset;

            // Вычисление нормали
            float[] normal = calculateNormal(v0, v1, v2);

            // Отсечение нелицевых граней
            float[] lightDir = {0, 0, 1}; // Направление света
            float cosTheta = dotProduct(normalize(normal), lightDir);

            if (cosTheta < 0) { // Отсекаем нелицевые грани
                // Базовое освещение
                int intensity = (int)(Math.abs(cosTheta) * 255);
                int color = (intensity << 16) | 130 | 130;

                // Отрисовка треугольника с z-буфером
                drawTriangleWithZBuffer(img, x0, y0, v0[2], x1, y1, v1[2], x2, y2, v2[2], color);
            }
        }

        img = flipImageVertically(img);
        File save = new File("model.png");
        ImageIO.write(img, "png", save);
    }

    public static float[] barycentricCoords(float x, float y, float x0, float y0, float x1, float y1, float x2, float y2) {
        float denominator = (x0 - x2) * (y1 - y2) - (x1 - x2) * (y0 - y2);

        float lambda0 = ((x - x2) * (y1 - y2) - (x1 - x2) * (y - y2)) / denominator;
        float lambda1 = ((x0 - x2) * (y - y2) - (x - x2) * (y0 - y2)) / denominator;
        float lambda2 = 1.0f - lambda0 - lambda1;

        return new float[]{lambda0, lambda1, lambda2};
    }


    // Отрисовка треугольника с z-буфером
    public static void drawTriangleWithZBuffer(BufferedImage img, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        int xmin = (int)Math.max(0, Math.min(x0, Math.min(x1, x2)));
        int xmax = (int)Math.min(img.getWidth() - 1, Math.max(x0, Math.max(x1, x2)));
        int ymin = (int)Math.max(0, Math.min(y0, Math.min(y1, y2)));
        int ymax = (int)Math.min(img.getHeight() - 1, Math.max(y0, Math.max(y1, y2)));

        for (int y = ymin; y <= ymax; y++) {
            for (int x = xmin; x <= xmax; x++) {
                float[] bary = barycentricCoords(x, y, x0, y0, x1, y1, x2, y2);

                if (bary[0] >= 0 && bary[1] >= 0 && bary[2] >= 0) {
                    // Интерполируем z-координату
                    float z = bary[0] * z0 + bary[1] * z1 + bary[2] * z2;

                    // Проверяем z-буфер
                    if (z < zBuffer[x][y]) {
                        img.setRGB(x, y, color);
                        zBuffer[x][y] = z;
                    }
                }
            }
        }
    }

    // Инициализация z-буфера
    public static void initializeZBuffer() {
        for (int i = 0; i < zBuffer.length; i++) {
            for (int j = 0; j < zBuffer[i].length; j++) {
                zBuffer[i][j] = Float.MAX_VALUE;
            }
        }
    }

    // Вычисление нормали к треугольнику
    public static float[] calculateNormal(float[] v0, float[] v1, float[] v2) {
        float[] u = {v1[0] - v0[0], v1[1] - v0[1], v1[2] - v0[2]};
        float[] v = {v2[0] - v0[0], v2[1] - v0[1], v2[2] - v0[2]};

        // Векторное произведение
        float[] normal = {
                u[1] * v[2] - u[2] * v[1],
                u[2] * v[0] - u[0] * v[2],
                u[0] * v[1] - u[1] * v[0]
        };

        return normal;
    }

    // Скалярное произведение
    public static float dotProduct(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    // Нормализация вектора
    public static float[] normalize(float[] v) {
        float length = (float)Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (length < 1e-8) return new float[]{0, 0, 0};
        return new float[]{v[0] / length, v[1] / length, v[2] / length};
    }

    public static BufferedImage flipImageVertically(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();
        BufferedImage flipped = new BufferedImage(width, height, original.getType());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                flipped.setRGB(x, height - 1 - y, original.getRGB(x, y));
            }
        }
        return flipped;
    }
}