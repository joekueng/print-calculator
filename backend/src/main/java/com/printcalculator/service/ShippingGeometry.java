package com.printcalculator.service;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Bounded STL inspection, performed once after slicing; never modifies the print mesh. */
public final class ShippingGeometry {
    private ShippingGeometry() {}
    private static final int MAX_VERTICES = 300_000;

    public static List<Map<String, Object>> inspect(Path stl) {
        try {
            if (stl == null || Files.size(stl) > 40_000_000) return List.of();
            List<double[]> points = new ArrayList<>();
            try (InputStream in = Files.newInputStream(stl)) {
                byte[] header = in.readNBytes(84);
                long count = header.length == 84 ? Integer.toUnsignedLong(ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(80)) : 0;
                if (header.length == 84 && 84 + count * 50 == Files.size(stl)) {
                    if (count * 3 > MAX_VERTICES) return List.of();
                    for (long i = 0; i < count; i++) {
                        byte[] bytes = in.readNBytes(50);
                        if (bytes.length != 50) return List.of();
                        ByteBuffer triangle = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                        triangle.position(12);
                        for (int v = 0; v < 3; v++) points.add(new double[]{triangle.getFloat(), triangle.getFloat(), triangle.getFloat()});
                    }
                } else {
                    try (BufferedReader reader = Files.newBufferedReader(stl)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String[] tokens = line.trim().split("\\s+");
                            if (tokens.length == 4 && "vertex".equalsIgnoreCase(tokens[0])) {
                                if (points.size() >= MAX_VERTICES) return List.of();
                                points.add(new double[]{Double.parseDouble(tokens[1]),Double.parseDouble(tokens[2]),Double.parseDouble(tokens[3])});
                            }
                        }
                    }
                }
            }
            if (points.size() < 3 || points.size() % 3 != 0
                    || points.stream().anyMatch(p -> Arrays.stream(p).anyMatch(v -> !Double.isFinite(v)))) return List.of();
            List<double[][]> frames = new ArrayList<>();
            frames.add(new double[][]{{1,0,0},{0,1,0},{0,0,1}});
            // Sample triangle frames uniformly; evaluate bounds against EVERY vertex.
            int triangles = points.size()/3;
            for (int t = 0; t < triangles && frames.size() < 33; t += Math.max(1, triangles/32)) {
                double[] a = points.get(t*3), b = points.get(t*3+1), c = points.get(t*3+2);
                double[] x = normalize(subtract(b,a)), z = normalize(cross(subtract(b,a),subtract(c,a)));
                if (x == null || z == null) continue;
                frames.add(new double[][]{x,cross(z,x),z});
            }
            List<Map<String,Object>> results = new ArrayList<>();
            for (double[][] frame : frames) {
                double[] min = {Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
                double[] max = {Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
                for (double[] point : points) for (int axis=0;axis<3;axis++) {
                    double value = dot(point, frame[axis]);
                    min[axis] = Math.min(min[axis],value); max[axis] = Math.max(max[axis],value);
                }
                // Round outward to micrometres so numerical error cannot create a false fit.
                List<Double> dims = new ArrayList<>();
                for (int axis=0;axis<3;axis++) dims.add(Math.ceil((max[axis]-min[axis])*1000 + 0.000001)/1000);
                results.add(Map.of("dimensions",dims,"basis",Arrays.stream(frame).map(v -> Arrays.stream(v).boxed().toList()).toList(),
                        "minimum",Arrays.stream(min).boxed().toList()));
            }
            return results;
        } catch (IOException | IllegalArgumentException ex) {
            // Existing authoritative slicer bounds remain the conservative fallback.
            return List.of();
        }
    }
    private static double[] subtract(double[] a,double[] b) { return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]}; }
    private static double dot(double[] a,double[] b) { return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]; }
    private static double[] cross(double[] a,double[] b) { return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]}; }
    private static double[] normalize(double[] v) {
        double length = Math.sqrt(dot(v,v));
        return length < 1e-10 || !Double.isFinite(length) ? null : new double[]{v[0]/length,v[1]/length,v[2]/length};
    }
}
