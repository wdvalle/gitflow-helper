package br.com.gitflowhelper.util;

import java.util.regex.Pattern;

public class HtmlGitCleaner {

    private static final Pattern GIT_C_PARAMS = Pattern.compile(
            "(?:-c\\s+[^\\s=]+=[^\\s]*\\s*)+"
    );

    public static String commentGitCParams(String html) {
        return GIT_C_PARAMS.matcher(html).replaceAll(match -> {
            String piece = match.group().trim();
            return "<!--" + piece + "-->";
        });
    }
}