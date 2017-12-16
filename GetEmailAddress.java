import java.io.BufferedReader;
import java.io.IOException;
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

    private void getHtmlContent(String webAddress) {


        String address = addProtocol(webAddress);

        if (!isPageValid(address)) {
            return;
        }

        try {
            URL url = new URL(address);
            BufferedReader read = new BufferedReader(new InputStreamReader(url.openStream()));
            StringBuilder contents = new StringBuilder();
            String input;
            while ((input = read.readLine()) != null) {
                contents.append(input);
            }

            extractEmails(contents.toString());

        } catch (IOException ex) {
        }
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


        //concate with parent url and return
        return parent + link;
    }

    private boolean isPageValid(String webUrl) {
        try {
            URL url = new URL(webUrl);
            URLConnection connection = url.openConnection();

            connection.connect();

            if (connection instanceof HttpURLConnection) {
                HttpURLConnection httpConnection = (HttpURLConnection) connection;
                int code = httpConnection.getResponseCode();
                String contentType = httpConnection.getContentType();

                return isHtmlLink(contentType) && isResponseSuccess(code);

            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    //Only success result,Not supporting redirect url or others
    private boolean isResponseSuccess(int responseCode) {
        if (responseCode == 200) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isHtmlLink(String contentType) {
        if (contentType.contains("text/html")) {
            return true;
        } else {
            return false;
        }
    }

    public static void main(String[] args) throws Exception {

        if (args.length == 1) {
            GetEmailAddress getEmailAddress = new GetEmailAddress(args[0]);
            getEmailAddress.parseEmailAddresses();
        }
    }
}
