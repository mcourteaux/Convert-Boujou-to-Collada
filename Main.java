import java.io.*;
import java.util.regex.*;
import java.util.*;
import java.util.stream.*;

public class Main {
    public static void usage() {
        System.out.println(
            "Usage: java Main --fps <fps> --text-input <boujou-text-file> --output <output-dae-file>");
        System.exit(0);
    }

    enum InputType { BOUJOU_TEXT }

    public static double[] matmat(double[] a, double[] b) {
        double[] res = new double[16];
        for (int r = 0; r < 4; ++r) {
            for (int c = 0; c < 4; ++c) {
                for (int i = 0; i < 4; ++i) {
                    res[r * 4 + c] += a[r * 4 + i] * b[i * 4 + c];
                }
            }
        }
        return res;
    }

    public static double[] matvec(double[] a, double[] b) {
        double[] res = new double[b.length];
        for (int r = 0; r < b.length; ++r) {
            for (int i = 0; i < b.length; ++i) {
                res[r] += a[r * 4 + i] * b[i];
            }
        }
        if (b.length == 3) {
            for (int i = 0; i < b.length; ++i) {
                res[i] += a[i * 4 + 3];
            }
        }
        return res;
    }

    static double fps = 25.0;
    static double imageWidth = 0;
    static double imageHeight = 0;
    static double sensorWidth = 0;
    static double sensorHeight = 0;

    static class CamSample {
        double[] rotation = new double[9];
        double[] translation = new double[9];
        double focallength;

        public double[] getAffineMatrix() {
            double[] a = new double[16];
            for (int i = 0; i < 9; ++i) {
                int c = i % 3;
                int r = i / 3;
                a[r * 4 + c] = rotation[i];
            }
            for (int i = 0; i < 3; ++i) {
                a[i * 4 + 3] = translation[i];
            }
            a[15] = 1.0;
            return a;
        }

        public double getFOVX(double sensorW) {
            return Math.toDegrees(2.0 * Math.atan(sensorW / (2.0 * focallength)));
        }
    }

    public static void parseBoujouText(
        BufferedReader reader, List<CamSample> track, List<double[]> points) throws IOException {
        String line;
        String parts[];
        int section = 0;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#Image Size ")) {
                parts = line.split("[ \t]+");
                imageWidth = Double.parseDouble(parts[2]);
                imageHeight = Double.parseDouble(parts[3]);
            } else if (line.startsWith("#Filmback Size ")) {
                parts = line.split("[ \t]+");
                sensorWidth = Double.parseDouble(parts[2]);
                sensorHeight = Double.parseDouble(parts[3]);
            } else if (line.startsWith("#")) {
                if (line.startsWith("#The Camera")) {
                    section = 1;
                } else if (line.startsWith("#3D Scene Points")) {
                    section = 2;
                }
            } else if (!line.isEmpty()) {
                if (section == 1) {
                    parts = line.split("[ \t]+");
                    CamSample s = new CamSample();
                    for (int i = 0; i < 9; ++i) {
                        s.rotation[i] = Double.parseDouble(parts[i]);
                    }
                    for (int i = 0; i < 3; ++i) {
                        s.translation[i] = Double.parseDouble(parts[9 + i]);
                    }
                    s.focallength = Double.parseDouble(parts[9 + 3]);
                    track.add(s);
                } else if (section == 2) {
                    parts = line.split("[ \t]+");
                    double[] p = new double[3];
                    for (int i = 0; i < 3; ++i) {
                        p[i] = Double.parseDouble(parts[i]);
                    }
                    points.add(p);
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        String inputFilename = null;
        String outputFilename = null;
        InputType type = null;
        try {
            for (int i = 0; i < args.length; ++i) {
                String a = args[i];
                if (a.equals("--fps")) {
                    fps = Double.parseDouble(args[++i]);
                } else if (a.equals("--text-input")) {
                    inputFilename = args[++i];
                    type = InputType.BOUJOU_TEXT;
                } else if (a.equals("--output")) {
                    outputFilename = args[++i];
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            usage();
        }

        if (inputFilename == null || outputFilename == null || type == null) {
            usage();
        }

        File inputFile = new File(inputFilename);
        if (!inputFile.exists()) {
            System.out.println("File not found: " + inputFilename);
            usage();
        }

        List<CamSample> cameraTrack = new ArrayList<>();
        List<double[]> scenePoints = new ArrayList<>();

        BufferedReader inputReader = new BufferedReader(new FileReader(inputFile));
        String[] parts;
        if (type == InputType.BOUJOU_TEXT) {
            parseBoujouText(inputReader, cameraTrack, scenePoints);
        }
        inputReader.close();

        // clang-format off
        double[] PrerotateCamera = {
            1, 0, 0, 0,
            0,-1, 0, 0,
            0, 0,-1, 0,
            0, 0, 0, 1
        };
        double[] BoujouToBlender = {
           -1, 0, 0, 0,
            0, 0, 1, 0,
            0, 1, 0, 0,
            0, 0, 0, 1
        };
        /*
        double[] PrerotateCamera = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
        };
        double[] BoujouToBlender = {
            1, 0, 0, 0,
            0,-1, 0, 0,
            0, 0,-1, 0,
            0, 0, 0, 1
        };
        */
        // clang-format on

        String TIME = IntStream.range(0, cameraTrack.size())
                          .mapToObj(i -> String.valueOf((1 + i) / fps))
                          .collect(Collectors.joining(" "));
        System.out.println("Time: " + TIME);

        String INTERPOLATION = IntStream.range(0, cameraTrack.size())
                                   .mapToObj(i -> "LINEAR")
                                   .collect(Collectors.joining(" "));

        String FRAMES = String.valueOf(cameraTrack.size());
        System.out.println("Frames: " + FRAMES);

        String FOV_X_INIT = String.valueOf(cameraTrack.get(0).getFOVX(sensorWidth));
        String ASPECT_RATIO = String.valueOf(sensorWidth / sensorHeight);

        String CAM_TRANSFORM_ANIM =
            cameraTrack.stream()
                .flatMap(c
                    -> DoubleStream
                           .of(matmat(
                               BoujouToBlender, matmat(c.getAffineMatrix(), PrerotateCamera)))
                           .mapToObj(v -> String.valueOf(v)))
                .collect(Collectors.joining(" "));
        System.out.println("Camera Track: " + CAM_TRANSFORM_ANIM);

        String CAM_TRANSFORM_INIT =
            DoubleStream
                .of(matmat(
                    BoujouToBlender, matmat(cameraTrack.get(0).getAffineMatrix(), PrerotateCamera)))
                .mapToObj(v -> String.valueOf(v))
                .collect(Collectors.joining(" "));

        String FOV_X_ANIM = cameraTrack.stream()
                                .map(c -> String.valueOf(c.getFOVX(sensorWidth)))
                                .collect(Collectors.joining(" "));
        System.out.println("FOV_X_ANIM: " + FOV_X_ANIM);

        String TRACKERPOINTS_COORDINATES =
            scenePoints.stream()
                .map(p -> matvec(BoujouToBlender, p))
                .flatMap(p -> DoubleStream.of(p).mapToObj(c -> String.valueOf(c)))
                .collect(Collectors.joining(" "));
        int lc = scenePoints.size() / 2;
        StringBuilder LINE_INDICES = new StringBuilder();
        for (int i = 0; i < lc; ++i) {
            LINE_INDICES.append(i * 2);
            LINE_INDICES.append(" ");
            LINE_INDICES.append(i * 2 + 1);
            LINE_INDICES.append(" ");
        }
        LINE_INDICES.setLength(LINE_INDICES.length() - 1);

        Map<String, String> replaceMap = new HashMap<>();
        replaceMap.put("TIME", TIME);
        replaceMap.put("FRAMES", FRAMES);
        replaceMap.put("FOV_X_INIT", FOV_X_INIT);
        replaceMap.put("ASPECT_RATIO", ASPECT_RATIO);
        replaceMap.put("CAM_TRANSFORM_ARRAY_LENGTH", String.valueOf(16 * cameraTrack.size()));
        replaceMap.put("CAM_TRANSFORM_ANIM", CAM_TRANSFORM_ANIM);
        replaceMap.put("INTERPOLATION", INTERPOLATION);
        replaceMap.put("FOV_X_ANIM", FOV_X_ANIM);
        replaceMap.put("CAM_TRANSFORM_INIT", CAM_TRANSFORM_INIT);
        replaceMap.put("TRACKERPOINTS_ARRAY_LENGTH", String.valueOf(3 * scenePoints.size()));
        replaceMap.put("TRACKERPOINTS_NUM", String.valueOf(scenePoints.size()));
        replaceMap.put("TRACKERPOINTS_COORDINATES", TRACKERPOINTS_COORDINATES);
        replaceMap.put("LINE_COUNT", String.valueOf(lc));
        replaceMap.put("LINE_INDICES", LINE_INDICES.toString());

        File templateFile = new File("template.dae");
        BufferedReader templateReader = new BufferedReader(new FileReader(templateFile));

        File outputFile = new File(outputFilename);
        PrintStream ps = new PrintStream(new FileOutputStream(outputFile));

        String line;
        Pattern pattern = Pattern.compile("\\$([A-Z_]+)\\$");
        while ((line = templateReader.readLine()) != null) {
            Matcher m = pattern.matcher(line);
            while (m.find()) {
                String var = m.group(1);
                System.out.println(var);

                String tgt = replaceMap.get(var);
                if (tgt == null) {
                    System.out.println("Unknown parameter to replace: " + var);
                    System.exit(1);
                }

                line = line.replace("$" + var + "$", tgt);
            }

            ps.println(line);
        }
        templateReader.close();
        ps.close();
    }
}
