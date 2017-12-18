
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetEmailAddress {

    private final String PROTOCOL_HTTP = "http://";
    private final String PROTOCOL_HTTPS = "https://";
    private String protocol;
    private String baseUrl;
    private Set<String> visitedLinks = new HashSet<>();
    private Set<String> collectedEmails = new HashSet<>();
    private Set<String> loopLinks = new HashSet<>();

    public GetEmailAddress(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void parseEmailAddresses() {
        setProtocol();
        getHtmlContent(baseUrl);
    }

    private void setProtocol() {
        if (baseUrl.startsWith(PROTOCOL_HTTPS)) {
            protocol = PROTOCOL_HTTPS;
        } else {
            protocol = PROTOCOL_HTTP;
        }
    }

    private void getHtmlContent(String webUrl) {
        String address = addProtocol(webUrl);
        try {
            Connection.Response response = Jsoup.connect(address).ignoreContentType(true)
                    .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/535.21 (KHTML, like Gecko) Chrome/19.0.1042.0 Safari/535.21")
                    .timeout(10*1000)
                    .followRedirects(true)
                    .execute();


            int code = response.statusCode();
            String contentType = response.contentType();

            if (code < 200 || code >= 400) {
                return;
            }
            if (!contentType.contains("text/html")) {
                return;
            }

            checkHeaderForRedirect(response.body(), webUrl);

        } catch (Exception e) {
        }
    }

    private void checkHeaderForRedirect(String contents, String webUrl) {
        Pattern pattern = Pattern.compile("(?<=<head>)(.*?)(?=</head>)");

        Matcher match = pattern.matcher(contents);

        Document document = null;

        while (match.find()) {

            document = Jsoup.parse(match.group());

        }

        if (document == null) {
            document = Jsoup.parse(contents);
        }

        String headUrl = RedirectHelper.getRedirectUrl(document);

        if (headUrl != null) {

            headUrl = getDomainLink(headUrl);
            if (headUrl == null || visitedLinks.contains(headUrl)) {
                return;
            }
        }

        extractEmails(contents, webUrl);

    }

    private void extractEmails(String contents, String parentLink) {
        contents = contents.replace("%20", "");
        String regex = "\\b[a-zA-Z0-9.-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z0-9.-]+\\b";
        Pattern pattern = Pattern.compile(regex);

        Matcher match = pattern.matcher(contents);

        while (match.find()) {
            if (!collectedEmails.contains(match.group())) {
                collectedEmails.add(match.group());
                System.out.println(match.group());
            }
        }

        extractChildLinks(contents, parentLink);

    }

    private void extractChildLinks(String contents, String parentLink) {
        try {
            Document document = Jsoup.parse(contents, addProtocol(parentLink));
            Elements links = document.select("a[href]");
            for (Element link : links) {
                //Get full url
                String url = link.attr("href");
                String childLink = getDomainLink(url);

                if (childLink == null) {
                    //Get Relative url
                    url = link.absUrl("href");
                    childLink = getDomainLink(url);
                }
                if (childLink != null) {
                    childLink = addProtocol(childLink);
                    if (!visitedLinks.contains(childLink)) {
                        visitedLinks.add(childLink);
                        getHtmlContent(childLink);
                    }
                }
            }
        } catch (Exception e) {

        }
    }

    private String removeProtocol(String url) {
        if (url.startsWith(protocol)) {
            url = url.replaceFirst(protocol, "");
        }

        return url;
    }

    private String addProtocol(String url) {
        if (!url.startsWith(protocol)) {
            url = protocol + url;
        }

        return url;
    }

    //Get a full non-subdomain link if there is
    private String getDomainLink(String link) {
        String parentLink = removeProtocol(baseUrl);
        String regex = "(?i)\\b" + parentLink + ".*\\b";
        Pattern pattern = Pattern.compile(regex);
        Matcher match = pattern.matcher(link);
        String domainLink = null;

        while (match.find()) {
            domainLink = match.group();
        }

        if (domainLink != null && !hasLinkLoop(domainLink)) {
            return domainLink;
        } else {
            return null;
        }

    }

    private boolean hasLinkLoop(String link) {

        String[] parts = link.split("/");

        if (loopLinks.contains(link) || parts.length > 15) {

            if (loopLinks.contains(link)) {

                String result = "";
                result = link.substring(0, link.lastIndexOf(parts[parts.length - 1]) - 1);
                loopLinks.add(result);
            } else {
                loopLinks.add(link);
            }

            visitedLinks.add(link);

            return true;
        }

        return false;
    }


    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            GetEmailAddress getEmailAddress = new GetEmailAddress(args[0]);
            getEmailAddress.parseEmailAddresses();
        }
    }
}
