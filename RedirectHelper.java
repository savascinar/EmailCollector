import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RedirectHelper {


    public static String getRedirectUrl(Document document) {

        String redirectUrl = getMeta(document);

        if(redirectUrl == null) {
            redirectUrl = getCanonical(document);
        }

        if(redirectUrl == null) {
            redirectUrl = getWindowLocation(document);
        }

        return redirectUrl;
    }


    public static String getMeta(Document document) {
        Elements elements = document.select("meta");
        if (elements != null && elements.tagName("HTTP-EQUIV") != null && elements.tagName("HTTP-EQUIV").first() != null) {
            Element e = elements.tagName("HTTP-EQUIV").first();
            if (e.attr("HTTP-EQUIV").toLowerCase().equals("refresh")) {
                String content = e.attr("content");
                return parseUrl(content);

            }
        }

        return null;
    }

    public static String getCanonical(Document document) {
        Elements elements = document.select("link");
        if (elements != null && elements.tagName("rel") != null && elements.tagName("rel").first() != null) {
            Elements linkElements = elements.tagName("rel");
            for (int i = 0; i < linkElements.size(); i++) {
                if (linkElements.get(i).attr("rel").equals("canonical")) {
                    return linkElements.get(i).attr("href");

                }
            }
        }

        return null;
    }


    public static String getWindowLocation(Document document) {
        Elements elements = document.select("script");
        if (elements != null && elements.tagName("window.location") != null && elements.tagName("window.location").first() != null) {

            Element e = elements.tagName("window.location").first();
            for (int i = 0; i < e.childNodeSize(); i++) {
                DataNode childNode = (DataNode) e.childNodes().get(i);
                String value = childNode.getWholeData();
                value = value.replace("\"", "");
                return parseUrl(value);

            }

        }

        return null;

    }

    private static String parseUrl(String content) {

        String regex = "\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";

        Pattern pattern = Pattern.compile(regex);

        Matcher match = pattern.matcher(content);

        while (match.find()) {
            return match.group();
        }

        return null;
    }
}
