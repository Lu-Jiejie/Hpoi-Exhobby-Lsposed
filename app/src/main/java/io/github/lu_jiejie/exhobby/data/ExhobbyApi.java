package io.github.lu_jiejie.exhobby.data;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client for www.exhobby.net.
 *
 * <p>{@code /search/item} answers with a redirect straight to the item's gallery page, and that
 * page already renders the first twenty images server-side. Since only the first few are shown
 * in-app, that single response is all this class ever needs.
 */
public final class ExhobbyApi {

    public static final String UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    /** Gallery pages sit behind an 18+ gate; the cookie keeps that gate from intercepting. */
    private static final String COOKIE = "is18=yes";

    private static final String PIC_HOST = "res.e39x.com/pic/";
    private static final String BASE = "https://www.exhobby.net";
    private static final String EMPTY_GALLERY = "-1";

    /** Only the first few images are loaded in-app; the rest stay in the browser. */
    public static final int LIMIT = 10;

    private static final Map<String, Gallery> CACHE = new HashMap<>();

    private ExhobbyApi() {
    }

    /** Immutable snapshot of one item's gallery. */
    public static final class Gallery {
        public final List<String> paths;
        public final String pageUrl;
        public final String error;
        /** True when the item has more images than {@link #LIMIT}. */
        public final boolean truncated;

        Gallery(List<String> paths, String pageUrl, String error, boolean truncated) {
            this.paths = paths;
            this.pageUrl = pageUrl;
            this.error = error;
            this.truncated = truncated;
        }

        public boolean isEmpty() {
            return paths.isEmpty();
        }
    }

    public interface Callback {
        void onGallery(Gallery gallery);
    }

    /** Public CDN URL for one image; {@code size} is {@code s}, {@code n} or {@code raw}. */
    public static String imgUrl(String path, String size) {
        return "https://" + PIC_HOST + size + "/" + path;
    }

    /**
     * Browser entry point for one item. The server 302s this to the signed gallery page, so the
     * link works before any API call has been made.
     */
    public static String browseUrl(int itemId, String itemType) {
        final String type = (itemType == null || itemType.isEmpty()) ? "hobby" : itemType;
        return BASE + "/search/item?type=" + encode(type) + "&itemId=" + itemId;
    }

    /**
     * Resolves one gallery. The callback runs on the calling thread, so callers on a UI thread
     * must post the result themselves.
     */
    public static Gallery load(int itemId, String itemType, Callback callback) {
        if (itemId <= 0) {
            return new Gallery(new ArrayList<String>(), null, "no-id", false);
        }
        final String type = (itemType == null || itemType.isEmpty()) ? "hobby" : itemType;
        final String key = type + ":" + itemId;
        synchronized (CACHE) {
            Gallery hit = CACHE.get(key);
            if (hit != null) {
                if (callback != null) {
                    callback.onGallery(hit);
                }
                return hit;
            }
        }
        Gallery g = fetch(itemId, type);
        if (g.error == null) {
            synchronized (CACHE) {
                if (CACHE.size() > 64) {
                    CACHE.clear();
                }
                CACHE.put(key, g);
            }
        }
        if (callback != null) {
            callback.onGallery(g);
        }
        return g;
    }

    private static Gallery fetch(int itemId, String type) {
        try {
            // The redirect is https -> http, which HttpURLConnection refuses to follow,
            // so resolve it by hand and upgrade the target back to https.
            String pageUrl = toHttps(locationOf(browseUrl(itemId, type)));
            if (pageUrl.isEmpty()) {
                return error("network");
            }
            String html = get(pageUrl);
            if (html == null) {
                return error("network");
            }
            String item = extractItem(html);
            if (item == null || EMPTY_GALLERY.equals(item)) {
                return new Gallery(new ArrayList<String>(), pageUrl, null, false);
            }

            LinkedHashSet<String> all = new LinkedHashSet<>();
            extractPaths(html, all);
            List<String> paths = new ArrayList<>();
            for (String p : all) {
                if (paths.size() >= LIMIT) {
                    break;
                }
                paths.add(p);
            }
            return new Gallery(paths, pageUrl, null, all.size() > LIMIT);
        } catch (Throwable t) {
            return error("error");
        }
    }

    private static Gallery error(String what) {
        return new Gallery(new ArrayList<String>(), null, what, false);
    }

    /** The gallery page carries its own numeric id: {@code query.item = "46163";}. */
    static String extractItem(String html) {
        int at = html.indexOf("query.item");
        if (at < 0) {
            return null;
        }
        int open = html.indexOf('"', at);
        int close = open < 0 ? -1 : html.indexOf('"', open + 1);
        return close < 0 ? null : html.substring(open + 1, close).trim();
    }

    /** Collects every image path the gallery page renders server-side. */
    static void extractPaths(String html, Set<String> out) {
        int from = 0;
        while (true) {
            int at = html.indexOf(PIC_HOST, from);
            if (at < 0) {
                return;
            }
            int sizeEnd = html.indexOf('/', at + PIC_HOST.length());
            if (sizeEnd < 0) {
                return;
            }
            String size = html.substring(at + PIC_HOST.length(), sizeEnd);
            int end = sizeEnd + 1;
            while (end < html.length() && "\"' <>\\".indexOf(html.charAt(end)) < 0) {
                end++;
            }
            if ("s".equals(size) || "n".equals(size) || "raw".equals(size)) {
                String path = html.substring(sizeEnd + 1, end);
                if (looksLikeImage(path)) {
                    out.add(path);
                }
            }
            from = Math.max(end, at + 1);
        }
    }

    private static boolean looksLikeImage(String path) {
        if (path.isEmpty() || path.indexOf('?') >= 0 || path.indexOf('&') >= 0) {
            return false;
        }
        String p = path.toLowerCase();
        return p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png") || p.endsWith(".webp");
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }

    private static String toHttps(String url) {
        return url.startsWith("http://") ? "https://" + url.substring(7) : url;
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "text/html,application/json,*/*");
        c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        c.setRequestProperty("Cookie", COOKIE);
        return c;
    }

    private static String read(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            return null;
        }
        InputStream in = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bo.write(buf, 0, n);
        }
        in.close();
        return new String(bo.toByteArray(), "UTF-8");
    }

    /** Follows one redirect by hand; empty when there is none. */
    static String locationOf(String url) {
        HttpURLConnection c = null;
        try {
            c = open(url);
            c.setInstanceFollowRedirects(false);
            int code = c.getResponseCode();
            if (code < 300 || code >= 400) {
                return "";
            }
            String to = c.getHeaderField("Location");
            return to == null ? "" : to;
        } catch (Throwable t) {
            return "";
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    static String get(String url) {
        HttpURLConnection c = null;
        try {
            c = open(url);
            return read(c);
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }
}
