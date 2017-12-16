import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
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

        extractEmails(contents.toString());
    }

    public void extractEmails(String contents) {
        String regex = "\\b[a-zA-Z0-9.-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z0-9.-]+\\b";
        Pattern pattern = Pattern.compile(regex);

        Matcher match = pattern.matcher(contents);

        while (match.find()) {
            if (!collectedEmails.contains(match.group())) {
                collectedEmails.add(match.group());
                System.out.println(match.group());
            }
        }
        extractChildLinks(contents);
    }

    public void extractChildLinks(String contents) {
        String parentLink = removeProtocol(baseUrl);
        Pattern patern = Pattern.compile("(?i)<a([^>]+)>(.+?)</a>");
        Matcher match = patern.matcher(contents);
        while (match.find()) {
            String hrefContent = match.group(1);

            Pattern hrefPattern = Pattern.compile("\\s*(?i)href\\s*=\\s*(\"([^\"]*\")|'[^']*'|([^'\">\\s]+))");
            Matcher hrefMatch = hrefPattern.matcher(hrefContent);

            while (hrefMatch.find()) {

                String childlink = getLink(hrefMatch.group(1), parentLink);

                if (childlink != null) {
                    childlink = addProtocol(childlink);
                    if (!visitedLinks.contains(childlink)) {
                        visitedLinks.add(childlink);
                        getHtmlContent(childlink);
                    }
                }
            }
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

    public String getLink(String link, String parent) {
        //Check if there is a full non-subdomain link
        String regex = "(?i)\\b" + parent + ".*\\b";
        Pattern pattern = Pattern.compile(regex);
        Matcher match = pattern.matcher(link);
        while (match.find()) {
            return match.group();
        }

        link = link.replace("\"", "");

        //If it is different url from parent(start with http, https or mail link, return null

        if (link.startsWith("mailto:") || link.startsWith("//") || link.startsWith(PROTOCOL_HTTP) || link.startsWith(PROTOCOL_HTTPS)) {
            return null;
        }

        if(!link.startsWith("/")) {
            link = "/" + link;
        }

        //concate with parent url and return
        return parent + link;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            GetEmailAddress getEmailAddress = new GetEmailAddress(args[0]);
            getEmailAddress.parseEmailAddresses();
        }
    }
}
