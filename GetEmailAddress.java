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

    private void extractEmails(String contents) {
        contents = contents.replace("%20","");
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

    private void extractChildLinks(String contents) {
        Pattern patern = Pattern.compile("(?i)<a([^>]+)>(.+?)</a>");
        Matcher match = patern.matcher(contents);
        while (match.find()) {
            String hrefContent = match.group(1);

            Pattern hrefPattern = Pattern.compile("\\s*(?i)href\\s*=\\s*(\"([^\"]*\")|'[^']*'|([^'\">\\s]+))");
            Matcher hrefMatch = hrefPattern.matcher(hrefContent);

            while (hrefMatch.find()) {

                String hrefLink = hrefMatch.group(1);
                String childLink = getFullLink(hrefLink);

                if(childLink == null){

                    childLink = getConcatenateLink(hrefLink);
                }

                if (childLink != null) {
                    childLink = addProtocol(childLink);
                    if (!visitedLinks.contains(childLink)) {
                        visitedLinks.add(childLink);
                        getHtmlContent(childLink);
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

    //Get a full non-subdomain link if there is
    private String getFullLink(String link) {
        String parentLink = removeProtocol(baseUrl);
        String regex = "(?i)\\b" + parentLink + ".*\\b";
        Pattern pattern = Pattern.compile(regex);
        Matcher match = pattern.matcher(link);
        while (match.find()) {
            return match.group();
        }

        return null;

    }

    private String getConcatenateLink(String link) {
        String parentLink = removeProtocol(baseUrl);
        link = link.replace("\"", "");

        if (link.startsWith("mailto:") || link.startsWith("//") || link.startsWith(PROTOCOL_HTTP) || link.startsWith(PROTOCOL_HTTPS)) {
            return null;
        }

        if(!link.startsWith("/")) {
            link = "/" + link;
        }

        return parentLink + link;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            GetEmailAddress getEmailAddress = new GetEmailAddress(args[0]);
            getEmailAddress.parseEmailAddresses();
        }
    }
}
