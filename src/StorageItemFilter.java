import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StorageItemFilter {
    private static final String SIZE_HELP =
            "Use size: followed by >, >=, <, or <=, then a number and "
                    + "B, KB, MB, GB, or TB (for example size:>=500MB).";
    private static final Pattern SIZE_TERM = Pattern.compile(
            "(?i)^size:(<=|>=|<|>)(\\d+(?:\\.\\d+)?)(b|kb|mb|gb|tb)$");

    private StorageItemFilter() {
    }

    public static List<StorageItem> filter(
            List<StorageItem> items,
            String query) {

        Objects.requireNonNull(items, "items");

        String strippedQuery = query == null ? "" : query.strip();
        if (strippedQuery.isEmpty()) {
            return List.copyOf(items);
        }

        List<String> textTerms = new ArrayList<>();
        List<SizeConstraint> sizeConstraints = new ArrayList<>();
        List<String> extensions = new ArrayList<>();
        for (String term : strippedQuery.split("\\s+")) {
            Matcher matcher = SIZE_TERM.matcher(term);
            if (matcher.matches()) {
                sizeConstraints.add(new SizeConstraint(
                        matcher.group(1),
                        bytes(matcher.group(2), matcher.group(3))));
            } else if (term.regionMatches(true, 0, "size:", 0, 5)) {
                throw new IllegalArgumentException(
                        "Invalid size filter \"" + term + "\". " + SIZE_HELP);
            } else if (term.regionMatches(true, 0, "ext:", 0, 4)) {
                String extension = normalize(term.substring(4));
                if (extension.startsWith(".")) {
                    extension = extension.substring(1);
                }
                if (!extension.matches("[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*")) {
                    throw new IllegalArgumentException(
                            "Invalid extension filter \"" + term
                                    + "\". Use ext:pdf or ext:tar.gz (an optional leading dot is allowed).");
                }
                extensions.add("." + extension);
            } else {
                textTerms.add(normalize(term));
            }
        }

        return items.stream()
                .filter(item -> matchesAll(item, textTerms, sizeConstraints, extensions))
                .toList();
    }

    private static boolean matchesAll(
            StorageItem item,
            List<String> textTerms,
            List<SizeConstraint> sizeConstraints,
            List<String> extensions) {

        for (String extension : extensions) {
            // Match the file's name, never a parent path or a dotted directory.
            if (item.directory() || !normalize(item.name()).endsWith(extension)) {
                return false;
            }
        }

        String searchable = normalize(String.join(" ",
                item.name(),
                item.file().getAbsolutePath(),
                item.typeLabel(),
                item.recommendationLabel(),
                item.storageType().name().replace('_', ' '),
                item.cleanupAction().name().replace('_', ' ')));

        for (String term : textTerms) {
            if (!searchable.contains(term)) {
                return false;
            }
        }

        for (SizeConstraint constraint : sizeConstraints) {
            if (!constraint.matches(item.sizeInBytes())) {
                return false;
            }
        }
        return true;
    }

    private static BigDecimal bytes(String amount, String unit) {
        long multiplier = switch (unit.toLowerCase(Locale.ROOT)) {
            case "b" -> 1L;
            case "kb" -> 1L << 10;
            case "mb" -> 1L << 20;
            case "gb" -> 1L << 30;
            case "tb" -> 1L << 40;
            default -> throw new IllegalArgumentException("Unsupported size unit: " + unit);
        };
        return new BigDecimal(amount).multiply(BigDecimal.valueOf(multiplier));
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.strip().toLowerCase(Locale.ROOT);
    }

    private record SizeConstraint(String operator, BigDecimal bytes) {
        private boolean matches(long actualBytes) {
            int comparison = BigDecimal.valueOf(Math.max(0, actualBytes))
                    .compareTo(bytes);
            return switch (operator) {
                case ">" -> comparison > 0;
                case ">=" -> comparison >= 0;
                case "<" -> comparison < 0;
                case "<=" -> comparison <= 0;
                default -> false;
            };
        }
    }
}
