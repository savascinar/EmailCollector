
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
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
        StringBuilder contents = new StringBuilder();
        String address = addProtocol(webUrl);
        try {
            URL url = new URL(address);
            URLConnection urlConnection = url.openConnection();

            if (urlConnection instanceof HttpURLConnection) {
                HttpURLConnection httpConnection = (HttpURLConnection) urlConnection;

                int code = httpConnection.getResponseCode();
                String contentType = httpConnection.getContentType();

                if (code != 200) {
                    return;
                }
                if (!contentType.contains("text/html")) {
                    return;
                }
            }

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));

            String input;
            while ((input = bufferedReader.readLine()) != null) {
                contents.append(input);
            }

            bufferedReader.close();

        } catch (Exception e) {
            return;
        }

        checkHeaderForRedirect(contents.toString(), webUrl);
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

        if (domainLink != null && !isLinkHasLoop(domainLink)) {
            return domainLink;
        } else {
            return null;
        }

    }

    private boolean isLinkHasLoop(String link) {

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
