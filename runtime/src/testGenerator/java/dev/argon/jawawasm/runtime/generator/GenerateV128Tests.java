package dev.argon.jawawasm.runtime.generator;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Generates direct Java calls; WAST files are read only during source generation. */
public final class GenerateV128Tests {
    private GenerateV128Tests() {}

    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger MASK64 = ONE.shiftLeft(64).subtract(ONE);
    private static final Pattern ASSERTION = Pattern.compile(
            "\\(assert_return\\s+\\(invoke\\s+\"([^\"]+)\"((?:\\s*\\((?:v128|i32|i64)\\.const\\s+[^()]+\\))+)\\s*\\)\\s+\\((v128|i32|i64)\\.const\\s+([^()]+)\\)\\s*\\)");
    private static final Pattern INVOCATION = Pattern.compile("\\(assert_return\\s+\\(invoke\\s+\"([^\"]+)\"");
    private static final Pattern CONSTANT = Pattern.compile("\\((v128|i32|i64)\\.const\\s+([^()]+)\\)");

    private static BigInteger integer(String token) {
        String value = token.trim().replace("_", "");
        boolean negative = value.startsWith("-");
        String magnitude = value.replaceFirst("^[+-]", "");
        BigInteger result = magnitude.startsWith("0x") || magnitude.startsWith("0X")
                ? new BigInteger(magnitude.substring(2), 16) : new BigInteger(magnitude);
        return negative ? result.negate() : result;
    }

    /** Exact positive rational, to avoid rounding f32 through double precision. */
    private record Rational(BigInteger numerator, BigInteger denominator) {
        Rational scale(int exponent) {
            return exponent >= 0 ? new Rational(numerator.shiftLeft(exponent), denominator)
                    : new Rational(numerator, denominator.shiftLeft(-exponent));
        }

        BigInteger roundEven() {
            BigInteger[] division = numerator.divideAndRemainder(denominator);
            int comparison = division[1].shiftLeft(1).compareTo(denominator);
            return comparison > 0 || (comparison == 0 && division[0].testBit(0))
                    ? division[0].add(ONE) : division[0];
        }
    }

    private static BigInteger floatBits(String token, int width) {
        String value = token.replace("_", "");
        BigInteger sign = value.startsWith("-") ? ONE.shiftLeft(width - 1) : BigInteger.ZERO;
        String magnitude = value.replaceFirst("^[+-]", "");
        int fractionBits = width == 32 ? 23 : 52;
        int exponentBits = width == 32 ? 8 : 11;
        BigInteger infinity = ONE.shiftLeft(exponentBits).subtract(ONE).shiftLeft(fractionBits);
        if (magnitude.equals("inf")) return sign.or(infinity);
        if (magnitude.startsWith("nan")) {
            BigInteger payload = magnitude.startsWith("nan:0x")
                    ? integer(magnitude.substring(4)) : ONE.shiftLeft(fractionBits - 1);
            return sign.or(infinity).or(payload);
        }
        Rational rational;
        if (magnitude.startsWith("0x")) {
            String[] parts = magnitude.substring(2).split("p", -1);
            String mantissa = parts[0];
            int dot = mantissa.indexOf('.');
            int digits = dot < 0 ? 0 : mantissa.length() - dot - 1;
            rational = new Rational(new BigInteger(mantissa.replace(".", ""), 16), ONE.shiftLeft(digits * 4))
                    .scale(parts.length == 1 || parts[1].isEmpty() ? 0 : Integer.parseInt(parts[1]));
        } else {
            BigDecimal decimal = new BigDecimal(magnitude);
            rational = decimal.scale() >= 0
                    ? new Rational(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale()))
                    : new Rational(decimal.unscaledValue().multiply(BigInteger.TEN.pow(-decimal.scale())), ONE);
        }
        if (rational.numerator.signum() == 0) return sign;
        int bias = (1 << (exponentBits - 1)) - 1;
        int exponent = rational.numerator.bitLength() - rational.denominator.bitLength();
        Rational normalized = rational.scale(-exponent);
        if (normalized.numerator.compareTo(normalized.denominator) < 0) --exponent;
        if (exponent < 1 - bias) return sign.or(rational.scale(fractionBits + bias - 1).roundEven());
        BigInteger mantissa = rational.scale(fractionBits - exponent).roundEven();
        if (mantissa.equals(ONE.shiftLeft(fractionBits + 1))) {
            mantissa = mantissa.shiftRight(1);
            ++exponent;
        }
        if (exponent > bias) return sign.or(infinity);
        return sign.or(BigInteger.valueOf(exponent + bias).shiftLeft(fractionBits))
                .or(mantissa.subtract(ONE.shiftLeft(fractionBits)));
    }

    private record Vector(BigInteger bits, int width, int canonical, int arithmetic) {
        String low() { return longLiteral(bits); }
        String high() { return longLiteral(bits.shiftRight(64)); }
        String expression() { return "vector(" + low() + ", " + high() + ")"; }
    }

    private static Vector vector(String literal) {
        String[] tokens = literal.trim().split("\\s+", -1);
        String shape = tokens[0];
        int width = Integer.parseInt(shape.substring(1, shape.indexOf('x')));
        if (tokens.length != 1 + 128 / width) throw new IllegalArgumentException(literal);
        BigInteger bits = BigInteger.ZERO;
        BigInteger mask = ONE.shiftLeft(width).subtract(ONE);
        int canonical = 0;
        int arithmetic = 0;
        for (int i = 1; i < tokens.length; ++i) {
            String token = tokens[i];
            BigInteger lane = shape.startsWith("i") ? integer(token) : floatBits(token, width);
            bits = bits.or(lane.and(mask).shiftLeft((i - 1) * width));
            if (token.equals("nan:canonical")) canonical |= 1 << (i - 1);
            if (token.equals("nan:arithmetic")) arithmetic |= 1 << (i - 1);
        }
        return new Vector(bits, width, canonical, arithmetic);
    }

    private static String longLiteral(BigInteger value) {
        return "0x" + String.format("%016x", value.and(MASK64)) + "L";
    }

    private static String literal(String kind, String value) {
        return switch (kind) {
            case "v128" -> vector(value).expression();
            case "i64" -> longLiteral(integer(value));
            case "i32" -> Integer.toString(integer(value).intValue());
            default -> throw new IllegalArgumentException(kind);
        };
    }

    private static void binary(Map<String, String> ops, String prefix, String suffix, String[][] names) {
        for (String[] pair : names) ops.put(prefix + pair[0], "{0}." + pair[1] + suffix + "({1})");
    }

    private static Map<String, String> operations() {
        Map<String, String> ops = new LinkedHashMap<>();
        for (int width : new int[]{8, 16, 32, 64}) {
            int count = 128 / width;
            String prefix = "i" + width + "x" + count + ".";
            for (String op : new String[]{"add", "sub", "neg", "abs", "mul"}) {
                if (op.equals("mul") && width == 8) continue;
                ops.put(prefix + op, "{0}." + op + width + "(" +
                        (op.equals("neg") || op.equals("abs") ? "" : "{1}") + ")");
            }
            binary(ops, prefix, Integer.toString(width), new String[][]{
                    {"eq", "equals"}, {"ne", "notEquals"}, {"lt_s", "lessThanSigned"},
                    {"lt_u", "lessThanUnsigned"}, {"gt_s", "greaterThanSigned"}, {"gt_u", "greaterThanUnsigned"},
                    {"le_s", "lessThanOrEqualSigned"}, {"le_u", "lessThanOrEqualUnsigned"},
                    {"ge_s", "greaterThanOrEqualSigned"}, {"ge_u", "greaterThanOrEqualUnsigned"},
                    {"shl", "shl"}, {"shr_s", "shrS"}, {"shr_u", "shrU"}});
            ops.put(prefix + "any_true", "{0}.anyTrue() ? 1 : 0");
            ops.put(prefix + "all_true", "{0}.allTrue" + width + "() ? 1 : 0");
            ops.put(prefix + "bitmask", "{0}.bitmask" + width + "()");
            if (width <= 32) binary(ops, prefix, Integer.toString(width), new String[][]{
                    {"min_s", "minS"}, {"min_u", "minU"}, {"max_s", "maxS"}, {"max_u", "maxU"}});
            if (width <= 16) binary(ops, prefix, Integer.toString(width), new String[][]{
                    {"add_sat_s", "addSatS"}, {"add_sat_u", "addSatU"}, {"sub_sat_s", "subSatS"},
                    {"sub_sat_u", "subSatU"}, {"avgr_u", "avgrU"}});
            for (int index : new int[]{0, count - 1}) {
                String position = index == 0 ? "first" : "last";
                String shape = "i" + width + "x" + count;
                for (String suffix : width < 32 ? new String[]{"_s", "_u"} : new String[]{""}) {
                    String expression = "{0}.extractLane" + width + "(" + index + ")";
                    if (width < 32) expression = suffix.equals("_s") ? "(int)" + expression
                            : (width == 8 ? "Byte" : "Short") + ".toUnsignedInt(" + expression + ")";
                    ops.put(shape + "_extract_lane" + suffix + "-" + position, expression);
                }
                String cast = switch (width) { case 8 -> "byte"; case 16 -> "short"; case 32 -> "int"; default -> "long"; };
                ops.put(shape + "_replace_lane-" + position, "{0}.replaceLane" + width + "((" + cast + "){1}, " + index + ")");
            }
        }
        for (int width : new int[]{32, 64}) {
            String prefix = "f" + width + "x" + 128 / width + ".";
            for (String op : new String[]{"add", "sub", "mul", "div", "min", "max", "pmin", "pmax"})
                ops.put(prefix + op, "{0}." + op + "F" + width + "({1})");
            for (String op : new String[]{"abs", "neg", "sqrt", "ceil", "floor", "trunc", "nearest"})
                ops.put(prefix + op, "{0}." + op + "F" + width + "()");
            binary(ops, prefix, "F" + width, new String[][]{
                    {"eq", "equals"}, {"ne", "notEquals"}, {"lt", "lessThan"}, {"gt", "greaterThan"},
                    {"le", "lessThanOrEqual"}, {"ge", "greaterThanOrEqual"}});
        }
        for (String[] pair : new String[][]{{"not", "bitNot"}, {"and", "and"}, {"or", "or"},
                {"xor", "xor"}, {"andnot", "andNot"}, {"bitselect", "bitselect"}})
            ops.put(pair[0], "{0}." + pair[1] + "(" + (pair[0].equals("not") ? ""
                    : pair[0].equals("bitselect") ? "{1}, {2}" : "{1}") + ")");
        return ops;
    }

    private static List<String> fixtures() {
        List<String> result = new ArrayList<>();
        for (int width : new int[]{8, 16, 32, 64})
            for (String suffix : new String[]{"arith", "arith2", "cmp"})
                result.add("simd_i" + width + "x" + 128 / width + "_" + suffix + ".wast");
        result.addAll(List.of("simd_i8x16_sat_arith.wast", "simd_i16x8_sat_arith.wast",
                "simd_bitwise.wast", "simd_bit_shift.wast", "simd_boolean.wast", "simd_lane.wast"));
        for (int width : new int[]{32, 64})
            for (String suffix : new String[]{"arith", "cmp", "rounding", "pmin_pmax"})
                result.add("simd_f" + width + "x" + 128 / width + "_" + suffix + ".wast");
        return result;
    }

    private static String operationName(String fixture, String export) {
        return fixture.endsWith("_cmp.wast") && !export.contains(".")
                ? fixture.substring(5, fixture.length() - "_cmp.wast".length()) + "." + export : export;
    }

    public static void main(String[] args) throws IOException {
        Path suite = Path.of(args[0]);
        Path output = Path.of(args[1]).resolve("dev/argon/jawawasm/runtime/tests");
        Files.createDirectories(output);
        // This directory belongs to the generator; remove obsolete fixture classes.
        try (var files = Files.list(output)) {
            for (Path file : files.filter(path -> Objects.requireNonNull(path.getFileName()).toString().endsWith(".java")).toList()) {
                Files.delete(file);
            }
        }
        Map<String, String> ops = operations();
        int total = 0;
        for (String fixture : fixtures()) {
            String source = Files.readString(suite.resolve(fixture)).replaceAll(";;[^\\r\\n]*", "");
            int supported = 0;
            var invocations = INVOCATION.matcher(source);
            while (invocations.find()) if (ops.containsKey(operationName(fixture, Objects.requireNonNull(invocations.group(1))))) ++supported;
            Map<String, List<String>> methods = new LinkedHashMap<>();
            int parsed = 0;
            int line = 1;
            int countedThrough = 0;
            var assertions = ASSERTION.matcher(source);
            while (assertions.find()) {
                String op = operationName(fixture, Objects.requireNonNull(assertions.group(1)));
                if (!ops.containsKey(op)) continue;
                ++parsed;
                String expression = ops.get(op);
                var constants = CONSTANT.matcher(Objects.requireNonNull(assertions.group(2)));
                int index = 0;
                while (constants.find()) expression = expression.replace("{" + index++ + "}", literal(Objects.requireNonNull(constants.group(1)), Objects.requireNonNull(constants.group(2))));
                String expected = Objects.requireNonNull(assertions.group(4)).trim();
                String statement;
                if (Objects.requireNonNull(assertions.group(3)).equals("v128")) {
                    Vector vector = vector(expected);
                    statement = "assertVector(" + vector.low() + ", " + vector.high() + ", " + expression;
                    if (vector.canonical != 0 || vector.arithmetic != 0)
                        statement += ", " + vector.width + ", " + vector.canonical + ", " + vector.arithmetic;
                    statement += ");";
                } else statement = "assertEquals(" + literal(Objects.requireNonNull(assertions.group(3)), expected) + ", " + expression + ");";
                while (countedThrough < assertions.start()) {
                    if (source.charAt(countedThrough++) == '\n') ++line;
                }
                String method = op.replaceAll("[^a-zA-Z0-9]", "_");
                methods.computeIfAbsent(method, key -> new ArrayList<>()).add("\t\t// " + fixture + ":" + line + "\n\t\t" + statement);
            }
            if (parsed == 0 || parsed != supported)
                throw new IllegalStateException(fixture + ": supported=" + supported + ", parsed=" + parsed);
            StringBuilder className = new StringBuilder("V128");
            for (String part : fixture.substring(5, fixture.length() - 5).split("_", -1))
                className.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            className.append("Test");
            StringBuilder java = new StringBuilder("// Generated by :runtime:generateV128Tests; do not edit.\n")
                    .append("// Cases derived from webassembly-testsuite/").append(fixture).append(" (Apache-2.0).\n")
                    .append("package dev.argon.jawawasm.runtime.tests;\n\nimport org.junit.jupiter.api.Test;\n")
                    .append("import static org.junit.jupiter.api.Assertions.assertEquals;\n\npublic class ")
                    .append(className).append(" extends V128TestSupport {\n");
            List<String> bodies = new ArrayList<>();
            for (var method : methods.entrySet()) {
                List<String> statements = method.getValue();
                for (int start = 0; start < statements.size(); start += 100) {
                    String suffix = statements.size() > 100 ? Integer.toString(start / 100 + 1) : "";
                    bodies.add("\t@Test\n\tpublic void " + method.getKey() + suffix + "() {\n"
                            + String.join("\n", statements.subList(start, Math.min(start + 100, statements.size()))) + "\n\t}");
                }
            }
            java.append(String.join("\n\n", bodies)).append("\n}\n");
            Files.writeString(output.resolve(className + ".java"), java);
            total += parsed;
            System.out.println(className + ": " + parsed + " cases");
        }
        System.out.println("Total: " + total + " direct V128 cases");
    }
}
