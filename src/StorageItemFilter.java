import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StorageItemFilter {
    private static final Pattern SIZE_TERM = Pattern.compile(
            "(?i)^size:(<=|>=|<|>)(\\d+(?:\\.\\d+)?)(b|kb|mb|gb|tb)$");

    private StorageItemFilter() {
    }

    public static List<StorageItem> filter(
            List<StorageItem> items,
            String query) {

        Objects.requireNonNull(items, "items");

        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return List.copyOf(items);
        }

        List<String> textTerms = new ArrayList<>();
        List<SizeConstraint> sizeConstraints = new ArrayList<>();
        for (String term : normalizedQuery.split("\\s+")) {
            Matcher matcher = SIZE_TERM.matcher(term);
            if (matcher.matches()) {
                sizeConstraints.add(new SizeConstraint(
                        matcher.group(1),
                        bytes(matcher.group(2), matcher.group(3))));
            } else {
                textTerms.add(term);
            }
        }

        return items.stream()
                .filter(item -> matchesAll(item, textTerms, sizeConstraints))
                .toList();
    }

    private static boolean matchesAll(
            StorageItem item,
            List<String> textTerms,
            List<SizeConstraint> sizeConstraints) {

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
