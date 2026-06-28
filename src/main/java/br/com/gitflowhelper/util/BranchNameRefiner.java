package br.com.gitflowhelper.util;

import br.com.gitflow.tracker.GFTask;
import com.intellij.tasks.Task;
import org.jetbrains.annotations.NotNull;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class BranchNameRefiner {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");
    private static final Pattern DUPLICATE_HYPHENS = Pattern.compile("-+");
    private static final Pattern START_END_HYPHENS = Pattern.compile("^-|-$");

    public static String slugify(@NotNull GFTask task) {
        String id = task.getPresentableId();
        String summary = task.getSummary();
        
        return slugify(id, summary);
    }

    public static String slugify(String id, String summary) {
        if (summary == null || summary.isEmpty()) {
            return id != null ? id : "";
        }

        String cleanSummary = normalize(summary);
        cleanSummary = cleanSummary.toLowerCase(Locale.ROOT);
        cleanSummary = NON_ALPHANUMERIC.matcher(cleanSummary).replaceAll("-");
        cleanSummary = DUPLICATE_HYPHENS.matcher(cleanSummary).replaceAll("-");
        cleanSummary = START_END_HYPHENS.matcher(cleanSummary).replaceAll("");

        if (id != null && !id.isEmpty()) {
            return id + "-" + cleanSummary;
        }
        return cleanSummary;
    }

    private static String normalize(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
