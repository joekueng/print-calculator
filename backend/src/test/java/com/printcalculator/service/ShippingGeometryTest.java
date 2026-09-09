package com.printcalculator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShippingGeometryTest {
    @TempDir Path temp;
    @Test void tiltedGeometryGetsTightFrameWithAllVerticesEnclosed() throws Exception {
        double angle = Math.PI/4;
        double[][] vertices = {{0,0,0},{100,0,0},{0,60,0},{100,0,10},{0,60,10},{100,60,10}};
        ByteBuffer bytes = ByteBuffer.allocate(184).order(ByteOrder.LITTLE_ENDIAN);
        bytes.position(80); bytes.putInt(2);
        for(int t=0;t<2;t++) {
            bytes.putFloat(0).putFloat(0).putFloat(0);
            for(int v=0;v<3;v++) {
                double[] p = vertices[t*3+v];
                bytes.putFloat((float)(p[0]*Math.cos(angle)+p[2]*Math.sin(angle)));
                bytes.putFloat((float)p[1]);
                bytes.putFloat((float)(-p[0]*Math.sin(angle)+p[2]*Math.cos(angle)));
            }
            bytes.putShort((short)0);
        }
        Path file = temp.resolve("tilted.stl"); Files.write(file,bytes.array());
        var frames = ShippingGeometry.inspect(file);
        assertTrue(frames.stream().anyMatch(f -> {
            List<?> d = (List<?>)f.get("dimensions");
            return Math.abs(((Number)d.get(0)).doubleValue()-100)<0.01
                    && Math.abs(((Number)d.get(1)).doubleValue()-60)<0.01
                    && Math.abs(((Number)d.get(2)).doubleValue()-10)<0.01;
        }));
        var item = new com.printcalculator.entity.QuoteLineItem();
        item.setQuantity(1); item.setMaterialGrams(java.math.BigDecimal.TEN);
        List<?> original = (List<?>) frames.getFirst().get("dimensions");
        item.setBoundingBoxXMm(java.math.BigDecimal.valueOf(((Number)original.get(0)).doubleValue()));
        item.setBoundingBoxYMm(java.math.BigDecimal.valueOf(((Number)original.get(1)).doubleValue()));
        item.setBoundingBoxZMm(java.math.BigDecimal.valueOf(((Number)original.get(2)).doubleValue()));
        var service = new ShippingQuoteService(3,ShippingQuoteService.DEFAULT_PROFILES);
        assertEquals(new java.math.BigDecimal("9.00"),service.quote(List.of(item)).costChf());
        item.setPricingBreakdown(Map.of("shippingOrientations",frames));
        assertEquals(new java.math.BigDecimal("2.00"),service.quote(List.of(item)).costChf());
        assertArrayEquals(bytes.array(),Files.readAllBytes(file));
    }
    @Test void invalidAndMissingGeometryFallBack() throws Exception {
        assertTrue(ShippingGeometry.inspect(temp.resolve("missing.stl")).isEmpty());
        Path file = temp.resolve("bad.stl"); Files.writeString(file,"solid test\nvertex NaN 0 0\nvertex 1 0 0\nvertex 0 1 0\nendsolid");
        assertTrue(ShippingGeometry.inspect(file).isEmpty());
    }

    @Test void asciiGeometryInspectsEveryTriangleIncludingUnsampledExtremes() throws Exception {
        Path file = temp.resolve("ascii.stl");
        Files.writeString(file,"solid part\nfacet normal 0 0 1\nouter loop\nvertex 0 0 0\nvertex 10 0 0\nvertex 0 10 0\nendloop\nendfacet\nfacet normal 0 0 1\nouter loop\nvertex 0 0 2\nvertex 100 0 2\nvertex 0 10 2\nendloop\nendfacet\nendsolid part");
        var frames = ShippingGeometry.inspect(file);
        assertFalse(frames.isEmpty());
        List<?> dimensions = (List<?>) frames.getFirst().get("dimensions");
        assertTrue(((Number)dimensions.getFirst()).doubleValue() >= 100);
    }
}
