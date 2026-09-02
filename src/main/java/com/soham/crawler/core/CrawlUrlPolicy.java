package com.soham.crawler.core;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

final class CrawlUrlPolicy {
    private static final Set<String> NON_HTML_EXTENSIONS = Set.of(
            "7z", "avi", "avif", "bin", "bmp", "css", "csv", "doc", "docx", "dmg", "eot", "exe",
            "gif", "gz", "ico", "iso", "jar", "jpeg", "jpg", "js", "json", "m4a", "mkv", "mov",
            "mp3", "mp4", "mpeg", "ogg", "otf", "pdf", "png", "ppt", "pptx", "rar", "rss", "svg",
            "tar", "tif", "tiff", "ttf", "wav", "webm", "webp", "woff", "woff2", "xls", "xlsx", "xml", "zip");

    private CrawlUrlPolicy() {}

    static boolean isLikelyHtml(URI url) {
        String path = url.getPath().toLowerCase(Locale.ROOT);
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot <= slash || !NON_HTML_EXTENSIONS.contains(path.substring(dot + 1));
    }
}
