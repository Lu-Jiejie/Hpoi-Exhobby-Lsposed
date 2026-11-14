package com.kelvin.hpoi.exhobby.data;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** One-off: shows why the search redirect fails under HttpURLConnection. */
public final class NetProbe {
    public static void main(String[] a) throws Exception {
        String url = "https://www.exhobby.net/search/item?type=hobby&itemId=3";
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", ExhobbyApi.UA);
        c.setRequestProperty("Cookie", "is18=yes");
        int code = c.getResponseCode();
        System.out.println("code=" + code);
        System.out.println("location=" + c.getHeaderField("Location"));
        System.out.println("final=" + c.getURL());
        c.disconnect();
    }
}
