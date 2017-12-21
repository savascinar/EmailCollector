
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetEmailAddress {

    private final int TEN_SECONDS = 10 * 1000;
    private final String PROTOCOL_HTTP = "http://";
    private final String PROTOCOL_HTTPS = "https://";
    private String protocol;
    private String baseUrl;
    private Set<String> visitedLinks = new HashSet<>();
    private Set<String> collectedEmails = new HashSet<>();
    private Integer maxDepth;

    public GetEmailAddress(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public GetEmailAddress(String baseUrl, int depth) {
        this.baseUrl = baseUrl;
        this.maxDepth = depth;
    }

    public void parseEmailAddresses() {
        setProtocol();
        getHtmlContent(baseUrl, 0);
    }

    private void setProtocol() {
        if (baseUrl.startsWith(PROTOCOL_HTTPS)) {
            protocol = PROTOCOL_HTTPS;
        } else {
            protocol = PROTOCOL_HTTP;
        }
    }

    private void getHtmlContent(String webUrl, int currentDepth) {
        String address = addProtocol(webUrl);
        try {

            Connection.Response response = Jsoup.connect(address).ignoreContentType(true)
                    .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/535.21 (KHTML, like Gecko) Chrome/19.0.1042.0 Safari/535.21")
                    .timeout(TEN_SECONDS)
                    .followRedirects(true)
                    .execute();

            int code = response.statusCode();
            String contentType = response.contentType();

            if (code < 200 || code >= 400) {
                visitedLinks.add(address);
                return;
            }
            if (!contentType.contains("text/html")) {
                visitedLinks.add(address);
                return;
            }

            String currentUrl;

            String host = response.url().getHost();
            String pathUrl = response.url().getPath();

            if (getDomainLink(host) == null) {
                return;
            } else {
                address = host;
            }

            if (pathUrl != null && pathUrl.length() > 1) {
                currentUrl = address + pathUrl;
            } else {
                currentUrl = address;
            }

            currentUrl = addProtocol(currentUrl);
            String domainLink = getDomainLink(currentUrl);

            if (domainLink == null) {
                visitedLinks.add(currentUrl);
            } else {
                if (!visitedLinks.contains((domainLink))) {
                    visitedLinks.add(domainLink);
                    extractEmails(response.body(), domainLink,currentDepth);
                }
            }

        } catch (Exception e) {
            visitedLinks.add(address);
        }
    }

    private void extractEmails(String contents, String parentLink,int currentDepth) {
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

        extractChildLinks(contents, parentLink,currentDepth);

    }

    private void extractChildLinks(String contents, String parentLink, int currentDepth) {
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

                        int newDepth = currentDepth + 1;
                        if (maxDepth == null) {
                            getHtmlContent(childLink, newDepth);
                        } else {
                            if (newDepth <= maxDepth) {
                                getHtmlContent(childLink, newDepth);
                            }
                        }

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
        link = removeProtocol(link);
        String parentLink = removeProtocol(baseUrl);
        String regex = "(?i)\\b" + parentLink + ".*\\b";
        Pattern pattern = Pattern.compile(regex);
        Matcher match = pattern.matcher(link);

        while (match.find()) {
            return match.group();
        }

        return null;

    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            GetEmailAddress getEmailAddress = new GetEmailAddress(args[0]);
            getEmailAddress.parseEmailAddresses();
        } else if (args.length == 2) {
            GetEmailAddress getEmailAddress = new GetEmailAddress(args[0], Integer.parseInt(args[1]));
            getEmailAddress.parseEmailAddresses();
        }
    }
}
