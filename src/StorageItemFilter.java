import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class StorageItemFilter {

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

        String[] terms = normalizedQuery.split("\\s+");
        return items.stream()
                .filter(item -> matchesAll(item, terms))
                .toList();
    }

    private static boolean matchesAll(
            StorageItem item,
            String[] terms) {

        String searchable = normalize(String.join(" ",
                item.name(),
                item.file().getAbsolutePath(),
                item.typeLabel(),
                item.recommendationLabel(),
                item.storageType().name().replace('_', ' '),
                item.cleanupAction().name().replace('_', ' ')));

        for (String term : terms) {
            if (!searchable.contains(term)) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.strip().toLowerCase(Locale.ROOT);
    }
}
