package com.printcalculator.service;

import com.printcalculator.entity.QuoteLineItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/** Conservative single-package packing. All lengths are millimetres, weights grams. */
@Service
public class ShippingQuoteService {
    public static final String DEFAULT_PROFILES = "LETTER,351,248,18,353,250,20,20,1000,2;SMALL,248,174,48,250,176,50,40,500,4;PARCEL_2,990,590,590,1000,600,600,300,2000,9;PARCEL_10,990,590,590,1000,600,600,300,10000,12;PARCEL_30,990,590,590,1000,600,600,500,30000,25";
    private final double padding;
    private final List<PackageProfile> profiles;
    private static final int[][] ROTATIONS = {{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};

    public ShippingQuoteService(
            @Value("${shipping.padding-mm:3}") double padding,
            @Value("${shipping.profiles:" + DEFAULT_PROFILES + "}") String configuration) {
        if (!Double.isFinite(padding) || padding < 0) throw new IllegalArgumentException("Invalid shipping padding");
        this.padding = padding;
        List<PackageProfile> parsed = new ArrayList<>();
        for (String entry : configuration.split(";")) {
            String[] p = entry.trim().split(",");
            if (p.length != 10) throw new IllegalArgumentException("Invalid shipping package profile");
            double[] values = new double[8];
            for (int i = 0; i < 8; i++) {
                values[i] = Double.parseDouble(p[i+1]);
                if (!Double.isFinite(values[i]) || values[i] <= 0) throw new IllegalArgumentException("Invalid package measurement");
            }
            double[] outer = {values[3], values[4], values[5]};
            Arrays.sort(outer);
            BigDecimal price = new BigDecimal(p[9]).setScale(2);
            boolean validTier = switch (price.intValueExact()) {
                case 2 -> outer[2] <= 353 && outer[1] <= 250 && outer[0] <= 20 && values[7] <= 1000;
                case 4 -> outer[2] <= 250 && outer[1] <= 176 && outer[0] <= 50 && values[7] <= 500;
                case 9 -> outer[2] <= 1000 && outer[1] <= 600 && outer[0] <= 600 && values[7] <= 2000;
                case 12 -> outer[2] <= 1000 && outer[1] <= 600 && outer[0] <= 600 && values[7] <= 10000;
                case 25 -> outer[2] <= 1000 && outer[1] <= 600 && outer[0] <= 600 && values[7] <= 30000;
                default -> false;
            };
            if (!validTier || values[0] > values[3] || values[1] > values[4] || values[2] > values[5]
                    || values[6] >= values[7]) throw new IllegalArgumentException("Package exceeds shipping tier limits");
            parsed.add(new PackageProfile(p[0], values[0], values[1], values[2], values[3], values[4], values[5],
                    BigDecimal.valueOf(values[6]), BigDecimal.valueOf(values[7]), price));
        }
        parsed.sort(Comparator.comparing(PackageProfile::price));
        profiles = List.copyOf(parsed);
    }

    public record PackageProfile(String code, double innerX, double innerY, double innerZ,
                                 double outerX, double outerY, double outerZ,
                                 BigDecimal packagingGrams, BigDecimal maxGrams, BigDecimal price) {}
    public record Placement(UUID itemId, int copy, double x, double y, double z,
                            double sizeX, double sizeY, double sizeZ, int orientation, int rotation) {}
    public record ShippingQuote(String status, BigDecimal costChf, PackageProfile packageProfile,
                                BigDecimal packedWeightGrams, double paddingMm, List<Placement> placements) {
        public boolean available() { return "QUOTED".equals(status) || "NOT_REQUIRED".equals(status); }
    }
    private record Unit(UUID id, int copy, List<double[]> orientations) {}
    private record Space(double x, double y, double z, double w, double h, double d) {}

    public ShippingQuote quote(List<QuoteLineItem> items) {
        if (items.isEmpty()) return result("NOT_REQUIRED");
        List<Unit> units = new ArrayList<>();
        BigDecimal weight = BigDecimal.ZERO;
        for (QuoteLineItem item : items) {
            if (item.getQuantity() == null || item.getQuantity() <= 0 || item.getMaterialGrams() == null
                    || item.getMaterialGrams().signum() <= 0) return result("PENDING");
            if (item.getStatus() != null && !"READY".equals(item.getStatus())) return result("PENDING");
            if (item.getQuantity() > 500 - units.size()) return result("MANUAL_QUOTE");
            List<double[]> orientations = dimensions(item);
            if (orientations.isEmpty()) return result("PENDING");
            weight = weight.add(item.getMaterialGrams().multiply(BigDecimal.valueOf(item.getQuantity())));
            for (int i = 0; i < item.getQuantity(); i++) units.add(new Unit(item.getId(), i, orientations));
        }
        // Large objects first; ties retain input order for reproducibility.
        units.sort(Comparator.comparingDouble((Unit u) -> Arrays.stream(u.orientations.getFirst()).reduce(1, (a,b) -> a*b)).reversed());
        for (PackageProfile profile : profiles) {
            BigDecimal packed = weight.add(profile.packagingGrams);
            if (packed.compareTo(profile.maxGrams) > 0) continue;
            // Try different guillotine split orders; spaces remain disjoint by construction.
            for (int split = 0; split < 3; split++) {
                List<Placement> placements = pack(units, profile, split);
                if (placements != null) return new ShippingQuote("QUOTED", profile.price, profile, packed, padding, placements);
            }
        }
        return result("MANUAL_QUOTE");
    }

    private ShippingQuote result(String status) {
        return new ShippingQuote(status, BigDecimal.ZERO.setScale(2), null, null, padding, List.of());
    }

    private List<double[]> dimensions(QuoteLineItem item) {
        List<double[]> result = new ArrayList<>();
        if (item.getPricingBreakdown() != null && item.getPricingBreakdown().get("shippingOrientations") instanceof List<?> frames) {
            for (Object frame : frames) {
                if (frame instanceof Map<?,?> map && map.get("dimensions") instanceof List<?> dims && dims.size() == 3
                        && dims.stream().allMatch(v -> v instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() > 0)) {
                    result.add(dims.stream().mapToDouble(v -> ((Number) v).doubleValue() + 2 * padding).toArray());
                }
            }
        }
        if (result.isEmpty()) {
            BigDecimal[] dims = {item.getBoundingBoxXMm(), item.getBoundingBoxYMm(), item.getBoundingBoxZMm()};
            if (Arrays.stream(dims).anyMatch(v -> v == null || v.signum() <= 0 || !Double.isFinite(v.doubleValue()))) return List.of();
            result.add(Arrays.stream(dims).mapToDouble(v -> v.doubleValue() + 2 * padding).toArray());
        }
        return result;
    }

    private List<Placement> pack(List<Unit> units, PackageProfile p, int split) {
        List<Space> spaces = new ArrayList<>(List.of(new Space(0,0,0,p.innerX,p.innerY,p.innerZ)));
        List<Placement> placed = new ArrayList<>();
        for (Unit unit : units) {
            int bestSpace = -1, bestFrame = 0, bestRotation = 0;
            double best = Double.POSITIVE_INFINITY;
            double[] size = null;
            for (int s = 0; s < spaces.size(); s++) {
                Space box = spaces.get(s);
                for (int f = 0; f < unit.orientations.size(); f++) {
                    double[] dim = unit.orientations.get(f);
                    for (int r = 0; r < ROTATIONS.length; r++) {
                        int[] axes = ROTATIONS[r];
                        double x = dim[axes[0]], y = dim[axes[1]], z = dim[axes[2]];
                        if (x > box.w || y > box.h || z > box.d) continue;
                        double score = box.w*box.h*box.d + x*y*z;
                        if (score < best) {
                            best = score; bestSpace = s; bestFrame = f; bestRotation = r; size = new double[]{x,y,z};
                        }
                    }
                }
            }
            if (bestSpace < 0) return null;
            Space box = spaces.remove(bestSpace);
            double x = size[0], y = size[1], z = size[2];
            placed.add(new Placement(unit.id, unit.copy, box.x, box.y, box.z, x,y,z,bestFrame,bestRotation));
            if (split == 0) {
                add(spaces, new Space(box.x+x,box.y,box.z,box.w-x,box.h,box.d));
                add(spaces, new Space(box.x,box.y+y,box.z,x,box.h-y,box.d));
                add(spaces, new Space(box.x,box.y,box.z+z,x,y,box.d-z));
            } else if (split == 1) {
                add(spaces, new Space(box.x+x,box.y,box.z,box.w-x,y,box.d));
                add(spaces, new Space(box.x,box.y+y,box.z,box.w,box.h-y,box.d));
                add(spaces, new Space(box.x,box.y,box.z+z,x,y,box.d-z));
            } else {
                add(spaces, new Space(box.x+x,box.y,box.z,box.w-x,box.h,z));
                add(spaces, new Space(box.x,box.y+y,box.z,x,box.h-y,z));
                add(spaces, new Space(box.x,box.y,box.z+z,box.w,box.h,box.d-z));
            }
        }
        return List.copyOf(placed);
    }
    private void add(List<Space> spaces, Space space) {
        if (space.w > 0 && space.h > 0 && space.d > 0) spaces.add(space);
    }
}
