import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;

public class AIChatApp extends MIDlet implements CommandListener {
  private static final char[] Encrypt_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

  private static final byte[] Encrypt_DECODE_TABLE = new byte[128];

  static {
    int i;
    for (i = 0; i < Encrypt_DECODE_TABLE.length; i++)
      Encrypt_DECODE_TABLE[i] = -1;
    for (i = 0; i < Encrypt_ALPHABET.length; i++)
      Encrypt_DECODE_TABLE[Encrypt_ALPHABET[i]] = (byte)i;
    Encrypt_DECODE_TABLE[61] = 0;
  }

  private static class Cookie {
    String name;

    String value;

    String domain;

    String path;

    Cookie(String name, String value, String domain, String path) {
      this.name = name;
      this.value = value;
      this.domain = domain;
      this.path = (path != null && path.length() > 0) ? path : "/";
    }

    public String toString() {
      return this.name + "=" + this.value;
    }
  }

  private Display display = Display.getDisplay(this);

  private Hashtable cookies = new Hashtable();

  private Vector logEntries = new Vector();

  private Form chatForm;

  private TextField messageInput;

  private StringItem chatHistory;

  private Command sendCommand;
  private Command backToSplashCommand;
  private Command backCommand;

  private Form splashScreen;
  private Command continueCommand;
  private Command exitAppCommand;
  private TextField languageInput;
  private String selectedLanguage = "";
  private TextField nameInput;
  private String selectedName = "";
  private TextField roleInput;
  private String selectedRole = "";

  private Form logForm;

  private StringItem logHistoryStringItem;

  private Image appIcon;
  
  // Listen need Base64 link not the normal one
  private static final String ENCODED_GLYPE_PROXY_BASE_URL = " ";

  private static final String ENCODED_PRIMARY_API_URL_TEMPLATE = " ";

  private static final String ENCODED_SECONDARY_API_URL_TEMPLATE = " ";

  private static final String ENCODED_RAPI_DOMAIN = " ";

  private static final String ENCODED_PROXY_DOMAIN = " ";

  private String glypeProxyBaseUrl;

  private String primaryApiUrlTemplate;

  private String secondaryApiUrlTemplate;

  private String hiddenApiDomain;

  private String hiddenRateavonDomain;

  private long lastRequestFailedTime = 0;
  private static final long ERROR_COOLDOWN_MILLIS = 10000;

  private String conversationSummary = "";
  private static final int MAX_SUMMARY_LENGTH = 50;

  public AIChatApp() {
    try {
      this.glypeProxyBaseUrl = decodeEncrypt(ENCODED_GLYPE_PROXY_BASE_URL);
      this.primaryApiUrlTemplate = decodeEncrypt(ENCODED_PRIMARY_API_URL_TEMPLATE);
      this.secondaryApiUrlTemplate = decodeEncrypt(ENCODED_SECONDARY_API_URL_TEMPLATE);
      this.hiddenApiDomain = decodeEncrypt(ENCODED_RAPI_DOMAIN);
      this.hiddenRateavonDomain = decodeEncrypt(ENCODED_PROXY_DOMAIN);
      log("URLs decoded.");
      log("Pri. API tmpl: " + replaceAll(this.primaryApiUrlTemplate, this.hiddenApiDomain, "url"));
      log("Sec. API tmpl: " + replaceAll(this.secondaryApiUrlTemplate, this.hiddenApiDomain, "url"));
      log("Glype Proxy Base URL masked.");
    } catch (IOException e) {
      log("URL decode err: " + e.getMessage());
      System.err.println("Error decoding URLs: " + e.getMessage());
      this.glypeProxyBaseUrl = "http://error.proxy/";
      this.primaryApiUrlTemplate = "http://error.api/";
      this.secondaryApiUrlTemplate = "http://error.api/";
      Alert alert = new Alert("Init Err", "Failed to init app.", null, AlertType.ERROR);
      this.display.setCurrent((Displayable)alert);
      return;
    }
    this.splashScreen = new Form("AI Chat V4.2");
    try {
      this.appIcon = Image.createImage("/icon.png");
      this.splashScreen.append((Item)new ImageItem(null, this.appIcon, 1, "App Icon"));
    } catch (IOException e) {
      log("Err load app icon: " + e.getMessage());
      System.err.println("Error loading app icon: " + e.getMessage());
      this.splashScreen.append((Item)new StringItem("", "Err load app icon."));
    }
    this.splashScreen.append((Item)new StringItem(null, "Model : ChatGPT\nV4.2"));

    this.languageInput = new TextField("Language:\n", "English", 128, TextField.ANY);
    this.splashScreen.append((Item)this.languageInput);

    this.nameInput = new TextField("Name:\n", "Carl", 128, TextField.ANY);
    this.splashScreen.append((Item)this.nameInput);

    this.roleInput = new TextField("Role:\n", "Helper", 128, TextField.ANY);
    this.splashScreen.append((Item)this.roleInput);

    this.continueCommand = new Command("Chat", Command.OK, 1);
    this.splashScreen.addCommand(this.continueCommand);

    this.exitAppCommand = new Command("Exit", Command.EXIT, 3);
    this.splashScreen.addCommand(this.exitAppCommand);

    this.splashScreen.setCommandListener(this);

    log("Splash scr init.");
    this.chatForm = new Form("AI Chat V4.2 : ChatGPT");
    this.chatHistory = new StringItem("Response:", "");
    this.messageInput = new TextField("Message:", "", 256, 0);
    this.sendCommand = new Command("Send", Command.OK, 1);
    this.backToSplashCommand = new Command("Back", Command.BACK, 2);
    this.chatForm.append((Item)this.chatHistory);
    this.chatForm.append((Item)this.messageInput);
    this.chatForm.addCommand(this.sendCommand);
    this.chatForm.addCommand(this.backToSplashCommand);
    this.chatForm.setCommandListener(this);
    log("Chat form init.");
    this.logForm = new Form("App Logs");
    this.logHistoryStringItem = new StringItem("Logs:", "");
    this.logForm.append((Item)this.logHistoryStringItem);
    this.backCommand = new Command("Back", Command.BACK, 1);
    this.logForm.addCommand(this.backCommand);
    this.logForm.setCommandListener(this);
    log("Log form init.");
  }

  private void log(String message) {
    String maskedMessage = message;
    maskedMessage = replaceAll(maskedMessage, this.hiddenApiDomain, "url");
    maskedMessage = replaceAll(maskedMessage, this.hiddenRateavonDomain, "url");
    String prefixedMessage = "> " + maskedMessage;
    this.logEntries.addElement(prefixedMessage);
    while (this.logEntries.size() > 50)
      this.logEntries.removeElementAt(0);

    this.display.callSerially(new Runnable() {
          public void run() {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < AIChatApp.this.logEntries.size(); i++) {
              sb.append(AIChatApp.this.logEntries.elementAt(i));
              sb.append('\n');
            }
            AIChatApp.this.logHistoryStringItem.setText(sb.toString());
          }
        });
    System.out.println(prefixedMessage);
  }

  protected void startApp() {
    log("App start.");
    this.display.setCurrent((Displayable)this.splashScreen);
    log("Disp. splash scr.");
  }

  protected void pauseApp() {
    log("App paused.");
  }

  protected void destroyApp(boolean unconditional) {
    log("App destroy.");
    notifyDestroyed();
  }

  public void commandAction(Command c, Displayable d) {
    if (c == this.sendCommand) {
      log("Send cmd.");
      sendMessage();
    } else if (c == this.backToSplashCommand && d == this.chatForm) {
      log("Back to Splash cmd from chat form.");
      this.display.setCurrent((Displayable)this.splashScreen);
    } else if (c == this.backCommand && d == this.logForm) {
      log("Back cmd from log.");
      this.display.setCurrent((Displayable)this.chatForm);
    } else if (c == this.continueCommand && d == this.splashScreen) {
        log("Continue cmd on splash screen.");
        this.selectedLanguage = this.languageInput.getString().trim();
        this.selectedName = this.nameInput.getString().trim();
        this.selectedRole = this.roleInput.getString().trim();

        log("Selected language: " + (this.selectedLanguage.length() > 0 ? this.selectedLanguage : "None"));
        log("Selected name: " + (this.selectedName.length() > 0 ? this.selectedName : "None"));
        log("Selected role: " + (this.selectedRole.length() > 0 ? this.selectedRole : "None"));
        this.display.setCurrent((Displayable)this.chatForm);
    } else if (c == this.exitAppCommand && d == this.splashScreen) {
        log("Exit App cmd from splash.");
        destroyApp(true);
    }
  }

  private void sendMessage() {
    final String userMessage = this.messageInput.getString();
    if (userMessage == null || userMessage.length() == 0) {
      log("Empty msg.");
      Alert alert = new Alert("Error", "Please enter a message.", null, AlertType.ERROR);
      this.display.setCurrent(alert, (Displayable)this.chatForm);
      return;
    }

    if (lastRequestFailedTime > 0 && (System.currentTimeMillis() - lastRequestFailedTime < ERROR_COOLDOWN_MILLIS)) {
        long remainingTime = (ERROR_COOLDOWN_MILLIS - (System.currentTimeMillis() - lastRequestFailedTime)) / 1000;
        Alert alert = new Alert("Cooldown", "Please wait " + (remainingTime + 1) + " seconds before making another request.", null, AlertType.WARNING);
        this.display.setCurrent(alert, (Displayable)this.chatForm);
        log("Request blocked due to cooldown.");
        return;
    }

    this.messageInput.setString("");
    log("User msg cleared: " + userMessage);

    this.display.callSerially(new Runnable() {
          public void run() {
            String currentChat = AIChatApp.this.chatHistory.getText();
            if (!currentChat.endsWith("\n"))
              currentChat = currentChat + "\n";
            currentChat += "User: " + userMessage + "\n";
            currentChat += "Response: AI Typing...\n";
            AIChatApp.this.chatHistory.setText(currentChat);
            AIChatApp.this.log("User msg and inline loading appended.");
          }
        });

    (new Thread(new Runnable() {
          public void run() {
            AIChatApp.this.log("API call thread start.");
            String aiAnswer = null;
            String errorMessage = null;
            try {
              AIChatApp.this.log("Pri. API call: " + AIChatApp.this.replaceAll(AIChatApp.this.primaryApiUrlTemplate, AIChatApp.this.hiddenApiDomain, "url"));
              aiAnswer = AIChatApp.this.callApi(AIChatApp.this.primaryApiUrlTemplate, userMessage);
              if (aiAnswer.startsWith("Error:"))
                throw new IOException("API ret. err: " + aiAnswer);
              AIChatApp.this.log("Pri. API call ok.");
              AIChatApp.this.lastRequestFailedTime = 0;
            } catch (Exception e) {
              errorMessage = "Pri. API fail: " + e.getMessage();
              AIChatApp.this.log(errorMessage);
              System.err.println(errorMessage);
              AIChatApp.this.lastRequestFailedTime = System.currentTimeMillis();
            }
            if (aiAnswer == null || aiAnswer.startsWith("Error:")) {
              AIChatApp.this.log("Pri. API fail. Try fallback.");

              AIChatApp.this.display.callSerially(new Runnable() {
                    public void run() {
                    }
                  });
              try {
                AIChatApp.this.log("Sec. API call: " + AIChatApp.this.replaceAll(AIChatApp.this.secondaryApiUrlTemplate, AIChatApp.this.hiddenApiDomain, "url"));
                aiAnswer = AIChatApp.this.callApi(AIChatApp.this.secondaryApiUrlTemplate, userMessage);
                if (aiAnswer.startsWith("Error:"))
                  throw new IOException("Fallback API ret. err: " + aiAnswer);
                AIChatApp.this.log("Sec. API call ok.");
                AIChatApp.this.lastRequestFailedTime = 0;
              } catch (Exception e) {
                errorMessage = "Sec. API fail: " + e.getMessage();
                AIChatApp.this.log(errorMessage);
                System.err.println(errorMessage);
                AIChatApp.this.lastRequestFailedTime = System.currentTimeMillis();
              }
            }
            final String finalAiAnswer = aiAnswer;
            final String finalErrorMessage = errorMessage;

            AIChatApp.this.display.callSerially(new Runnable() {
                  public void run() {
                    String currentChat = AIChatApp.this.chatHistory.getText();
                    String placeholder = "Response: AI Typing...\n";
                    if (currentChat.endsWith(placeholder)) {
                        currentChat = currentChat.substring(0, currentChat.length() - placeholder.length());
                    } else {
                        placeholder = "Response: AI Typing...";
                        if (currentChat.endsWith(placeholder)) {
                            currentChat = currentChat.substring(0, currentChat.length() - placeholder.length());
                        }
                    }

                    if (!currentChat.endsWith("\n"))
                        currentChat = currentChat + "\n";

                    if (finalAiAnswer != null && !finalAiAnswer.startsWith("Error:")) {
                      AIChatApp.this.chatHistory.setText(currentChat + "Response: " + finalAiAnswer);
                      AIChatApp.this.log("AI resp. disp. and loading removed.");
                      AIChatApp.this.updateConversationSummary(userMessage, finalAiAnswer);
                    } else {
                      String displayError = finalErrorMessage;
                      if (finalAiAnswer != null && finalAiAnswer.startsWith("Error:")) {
                        displayError = finalAiAnswer;
                      } else if (displayError == null) {
                        displayError = "Unknown err occurred.";
                      }
                      AIChatApp.this.log("Disp. err: " + displayError);
                      Alert alert = new Alert("Error", displayError, null, AlertType.ERROR);
                      AIChatApp.this.display.setCurrent(alert, (Displayable)AIChatApp.this.chatForm);
                      AIChatApp.this.chatHistory.setText(currentChat + "Response (Error): " + displayError);
                    }
                  }
                });
          }
        })).start();
  }

  private void updateConversationSummary(String userMessage, String aiResponse) {
      String newSegment = "User: " + userMessage + " AI: " + aiResponse;

      if (conversationSummary.length() + newSegment.length() > MAX_SUMMARY_LENGTH) {
          int overflow = (conversationSummary.length() + newSegment.length()) - MAX_SUMMARY_LENGTH;
          if (overflow < conversationSummary.length()) {
              conversationSummary = conversationSummary.substring(overflow);
          } else {
              conversationSummary = "";
          }
      }
      conversationSummary += newSegment;
      log("Conversation Summary updated: " + conversationSummary);
  }

  private String callApi(String apiUrlTemplate, String userMessage) throws IOException {
    HttpConnection httpConn = null;
    InputStream is = null;
    ByteArrayOutputStream baos = null;
    String result = null;
    log("callApi tmpl: " + replaceAll(apiUrlTemplate, this.hiddenApiDomain, "url"));
    try {
      StringBuffer urlBuilder = new StringBuffer(apiUrlTemplate);

      urlBuilder.append(URLEncode(userMessage));

      StringBuffer promptInstructions = new StringBuffer();
      boolean hasPreviousInstruction = false;

      if (conversationSummary != null && conversationSummary.length() > 0) {
          promptInstructions.append("Conversation so far: ");
          promptInstructions.append(conversationSummary);
          promptInstructions.append(". ");
          hasPreviousInstruction = true;
      }


      if (selectedLanguage != null && selectedLanguage.length() > 0) {
          if (hasPreviousInstruction) {
              promptInstructions.append("Also, ");
          }
          promptInstructions.append("Answer it with ");
          promptInstructions.append(selectedLanguage);
          hasPreviousInstruction = true;
      }

      if (selectedRole != null && selectedRole.length() > 0) {
          if (hasPreviousInstruction) {
              promptInstructions.append(" and assume the role of ");
          } else {
              promptInstructions.append("Assume the role of ");
          }
          promptInstructions.append(selectedRole);
          hasPreviousInstruction = true;
      }

      if (selectedName != null && selectedName.length() > 0) {
          if (hasPreviousInstruction) {
              promptInstructions.append(" and my name is ");
          } else {
              promptInstructions.append("My name is ");
          }
          promptInstructions.append(selectedName);
          hasPreviousInstruction = true;
      }

      if (hasPreviousInstruction) {
          promptInstructions.append(" ");
      }

      if (promptInstructions.length() > 0) {
          urlBuilder.append(", prompt=");
          urlBuilder.append(URLEncode(promptInstructions.toString()));
      }

      urlBuilder.append("");

      if (apiUrlTemplate.indexOf("/v2/") != -1)
        urlBuilder.append("&imageUrl=");

      String apiUrlWithText = urlBuilder.toString();

      String maskedApiUrlWithText = replaceAll(apiUrlWithText, this.hiddenApiDomain, "url");
      log("Const. API URL: " + ((maskedApiUrlWithText.length() > 100) ? (maskedApiUrlWithText.substring(0, 100) + "...") : maskedApiUrlWithText));
      String encodedApiUrl = URLEncode(apiUrlWithText);
      String finalGlypeUrl = this.glypeProxyBaseUrl + encodedApiUrl + "&hl=3e5";

      log("Final Glype Proxy URL (length " + finalGlypeUrl.length() + "): " +
          ((finalGlypeUrl.length() > 200) ? (finalGlypeUrl.substring(0, 200) + "...") : finalGlypeUrl));

      log("Open. HTTP conn.");
      System.out.println("Connecting to Glype Proxy: " + finalGlypeUrl);
      httpConn = (HttpConnection)Connector.open(finalGlypeUrl);
      log("HTTP conn. open.");
      httpConn.setRequestMethod("GET");
      httpConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; rv:2.0.1) Gecko/20100101 Firefox/4.0.1");
      log("Req. method & User-Agent set.");
      addCookiesToRequest(httpConn, finalGlypeUrl);
      log("Cookies add.");
      int responseCode = httpConn.getResponseCode();
      log("HTTP Resp. Code: " + responseCode);
      processSetCookieHeaders(httpConn, finalGlypeUrl);
      log("Set-Cookie headers proc.");
      if (responseCode == 200) {
        log("HTTP_OK. Read. resp.");
        is = httpConn.openInputStream();
        baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1)
          baos.write(buffer, 0, bytesRead);
        String rawGlypeResponse = new String(baos.toByteArray(), "UTF-8");
        log("Raw Glype Resp. len: " + rawGlypeResponse.length());
        String aiResponseJson = extractTextFromHtml(rawGlypeResponse);
        log("Extr. AI Resp. JSON/Text. Len: " + aiResponseJson.length());
        String aiAnswer = extractAnswerFromJson(aiResponseJson);
        log("Extr. AI Answer from JSON. Len: " + aiAnswer.length());
        aiAnswer = replaceAll(aiAnswer, "\\n", "\n");
        aiAnswer = replaceAll(aiAnswer, "\\r\\n", "\n");
        aiAnswer = replaceAll(aiAnswer, "\\r", "\n");
        log("Proc. newlines.");
        aiAnswer = processBoldText(aiAnswer);
        log("Proc. bold text.");
        result = aiAnswer;
      } else {
        log("HTTP Err: " + responseCode + " - " + httpConn.getResponseMessage());
        throw new IOException("HTTP Err: " + responseCode + " - " + httpConn.getResponseMessage());
      }
    } finally {
      log("Close. HTTP conn. res.");
      try {
        if (is != null)
          is.close();
        if (baos != null)
          baos.close();
        if (httpConn != null)
          httpConn.close();
        log("HTTP Conn. res. closed.");
      } catch (IOException e) {
        log("Err close conn: " + e.getMessage());
        System.err.println("Error closing connection: " + e.getMessage());
      }
    }
    return result;
  }

  private String processBoldText(String text) {
    log("Proc. bold text.");
    StringBuffer sb = new StringBuffer();
    int lastIndex = 0;
    int startIndex = 0;
    while ((startIndex = text.indexOf("**", lastIndex)) != -1) {
      sb.append(text.substring(lastIndex, startIndex));
      int endIndex = text.indexOf("**", startIndex + 2);
      if (endIndex != -1) {
        String boldContent = text.substring(startIndex + 2, endIndex);
        sb.append(boldContent.toUpperCase());
        lastIndex = endIndex + 2;
        continue;
      }
      log("Warn: Miss. bold close tag at " + startIndex);
      sb.append(text.substring(startIndex));
      lastIndex = text.length();
    }
    sb.append(text.substring(lastIndex, text.length()));
    log("Bold text proc. done.");
    return sb.toString();
  }

  private String extractAnswerFromJson(String jsonString) {
    log("Extr. AI answer from JSON.");
    int startIndex = jsonString.indexOf("\"result\":\"");
    if (startIndex != -1) {
      startIndex += "\"result\":\"".length();
      int endIndex = jsonString.indexOf("\"", startIndex);
      if (endIndex != -1) {
        log("AI answer extr. ok.");
        return jsonString.substring(startIndex, endIndex);
      }
    }
    log("Fail prs AI answer from JSON: " + jsonString);
    return "Error: Could not prs AI answer from JSON: " + jsonString;
  }

  private String URLEncode(String s) {
    String maskedS = replaceAll(s, this.hiddenApiDomain, "url");
    maskedS = replaceAll(maskedS, this.hiddenRateavonDomain, "url");
    log("URL enc: " + ((maskedS.length() > 50) ? (maskedS.substring(0, 50) + "...") : maskedS));
    if (s == null)
      return "";
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '.' || c == '-' || c == '*' || c == '_' || c == '/' || c == ':' || c == '?' || c == '=' || c == '&') {
        sb.append(c);
      } else if (c == ' ') {
        sb.append('+');
      } else {
        sb.append('%');
        sb.append(toHex(c >> 4 & 0xF));
        sb.append(toHex(c & 0xF));
      }
    }
    log("URL enc. done.");
    return sb.toString();
  }

  private char toHex(int ch) {
    return (char)((ch < 10) ? (48 + ch) : (65 + ch - 10));
  }

  private void addCookiesToRequest(HttpConnection conn, String url) {
    String maskedUrl = replaceAll(url, this.hiddenApiDomain, "url");
    maskedUrl = replaceAll(maskedUrl, this.hiddenRateavonDomain, "url");
    log("Add cookies for URL: " + maskedUrl);
    String host = getHostFromUrl(url);
    if (host == null) {
      log("No host for URL: " + maskedUrl + ". Skip cookie add.");
      return;
    }
    Vector domainCookies = (Vector)this.cookies.get(host);
    if (domainCookies == null) {
      int firstDot = host.indexOf('.');
      if (firstDot != -1) {
        String superDomain = host.substring(firstDot + 1);
        domainCookies = (Vector)this.cookies.get(superDomain);
      }
    }
    if (domainCookies != null && domainCookies.size() > 0) {
      StringBuffer cookieHeader = new StringBuffer();
      for (int i = 0; i < domainCookies.size(); i++) {
        Cookie c = (Cookie)domainCookies.elementAt(i);
        if (url.startsWith(c.path) || c.path.equals("/")) {
          if (cookieHeader.length() > 0)
            cookieHeader.append("; ");
          cookieHeader.append(c.toString());
        }
      }
      if (cookieHeader.length() > 0) {
        try {
          conn.setRequestProperty("Cookie", cookieHeader.toString());
          log("Sent Cookie Header: " + cookieHeader.toString());
          System.out.println("Sent Cookie Header: " + cookieHeader.toString());
        } catch (IOException e) {
          log("Err set Cookie header: " + e.getMessage());
          System.err.println("Error setting Cookie header: " + e.getMessage());
        }
      } else {
        log("No relevant cookies found.");
      }
    } else {
      String maskedHost = replaceAll(host, this.hiddenApiDomain, "url");
      maskedHost = replaceAll(maskedHost, this.hiddenRateavonDomain, "url");
      log("No cookies for host: " + maskedHost);
    }
  }

  private void processSetCookieHeaders(HttpConnection conn, String url) throws IOException {
    String maskedUrl = replaceAll(url, this.hiddenApiDomain, "url");
    maskedUrl = replaceAll(maskedUrl, this.hiddenRateavonDomain, "url");
    log("Proc. Set-Cookie headers for URL: " + maskedUrl);
    String host = getHostFromUrl(url);
    if (host == null) {
      log("No host for Set-Cookie proc. Skip.");
      return;
    }
    for (int i = 0;; i++) {
      String headerName = conn.getHeaderFieldKey(i);
      if (headerName == null) {
        log("No more Set-Cookie headers.");
        break;
      }
      if (headerName.toUpperCase().equals("SET-COOKIE")) {
        String cookieValue = conn.getHeaderField(i);
        log("Rcvd. Set-Cookie: " + cookieValue);
        System.out.println("Received Set-Cookie: " + cookieValue);
        parseAndStoreCookie(cookieValue, host);
      }
    }
  }

  private void parseAndStoreCookie(String setCookieHeader, String defaultDomain) {
    log("Prs. & store cookie: " + setCookieHeader);
    if (setCookieHeader == null || setCookieHeader.length() == 0) {
      log("Empty Set-Cookie header. Skip.");
      return;
    }
    String name = null;
    String value = null;
    String domain = defaultDomain;
    String path = "/";
    String[] parts = splitString(setCookieHeader, ';');
    log("Cookie header spl. into " + parts.length + " parts.");
    if (parts.length > 0) {
      int eqIndex = parts[0].indexOf('=');
      if (eqIndex != -1) {
        name = parts[0].substring(0, eqIndex).trim();
        value = parts[0].substring(eqIndex + 1).trim();
        log("Prs. cookie name: " + name + ", val: " + value);
      }
    }
    if (name == null || value == null) {
      log("Could not prs cookie name/val. Skip.");
      return;
    }
    for (int i = 1; i < parts.length; i++) {
      String part = parts[i].trim();
      if (part.length() != 0) {
        int eqIndex = part.indexOf('=');
        if (eqIndex != -1) {
          String attrName = part.substring(0, eqIndex).trim().toLowerCase();
          String attrValue = part.substring(eqIndex + 1).trim();
          if (attrName.equals("domain")) {
            if (attrValue.startsWith(".")) {
              domain = attrValue.substring(1);
            } else {
              domain = attrValue;
            }
            log("Prs. cookie domain: " + domain);
          } else if (attrName.equals("path")) {
            path = attrValue;
            log("Prs. cookie path: " + path);
          }
        }
      }
    }
    Cookie newCookie = new Cookie(name, value, domain, path);
    String maskedCookieDomain = replaceAll(newCookie.domain, this.hiddenApiDomain, "url");
    maskedCookieDomain = replaceAll(maskedCookieDomain, this.hiddenRateavonDomain, "url");
    log("New cookie. Store. for domain: " + maskedCookieDomain);
    Vector domainCookies = (Vector)this.cookies.get(domain);
    if (domainCookies == null) {
      domainCookies = new Vector();
      this.cookies.put(domain, domainCookies);
      log("Crt. new cookie vector for domain: " + maskedCookieDomain);
    }
    boolean updated = false;
    for (int j = 0; j < domainCookies.size(); j++) {
      Cookie existingCookie = (Cookie)domainCookies.elementAt(j);
      if (existingCookie.name.equals(newCookie.name) && existingCookie.path.equals(newCookie.path)) {
        domainCookies.setElementAt(newCookie, j);
        updated = true;
        log("Upd. exist. cookie: " + newCookie.name);
        break;
      }
    }
    if (!updated) {
      domainCookies.addElement(newCookie);
      log("Add. new cookie: " + newCookie.name);
    }
  }

  private String getHostFromUrl(String url) {
    int hostStart;
    String maskedUrlForLog = replaceAll(url, this.hiddenApiDomain, "url");
    maskedUrlForLog = replaceAll(maskedUrlForLog, this.hiddenApiDomain, "url");
    log("Extr. host from URL: " + ((maskedUrlForLog.length() > 50) ? (maskedUrlForLog.substring(0, 50) + "...") : maskedUrlForLog));
    if (url == null)
      return null;
    int protoEnd = url.indexOf("://");
    if (protoEnd == -1) {
      hostStart = 0;
    } else {
      hostStart = protoEnd + 3;
    }
    int pathStart = url.indexOf('/', hostStart);
    if (pathStart == -1) {
      int queryStart = url.indexOf('?', hostStart);
      if (queryStart == -1) {
        String str1 = url.substring(hostStart);
        log("Extr. host: " + replaceAll(str1, this.hiddenApiDomain, "url") + " (no path/query).");
        return str1;
      }
      String str = url.substring(hostStart, queryStart);
      log("Extr. host: " + replaceAll(str, this.hiddenApiDomain, "url") + " (with query).");
      return str;
    }
    String host = url.substring(hostStart, pathStart);
    log("Extr. host: " + replaceAll(host, this.hiddenApiDomain, "url") + " (with path).");
    return host;
  }

  private String extractTextFromHtml(String htmlContent) {
    log("HTML text extr. start.");
    if (htmlContent == null) {
      log("HTML content is null.");
      return "";
    }
    int preStartIndex = htmlContent.indexOf("<pre>");
    int preEndIndex = htmlContent.indexOf("</pre>");
    if (preStartIndex != -1 && preEndIndex != -1 && preEndIndex > preStartIndex) {
      String jsonContent = htmlContent.substring(preStartIndex + "<pre>".length(), preEndIndex);
      log("Found <pre> tags.");
      return jsonContent.trim();
    }
    log("No <pre> tags. Gen. HTML cleanup.");
    htmlContent = removeTagsAndContent(htmlContent, "script");
    htmlContent = removeTagsAndContent(htmlContent, "style");
    htmlContent = removeComments(htmlContent);
    log("Rm script/style/comments.");
    htmlContent = replaceAll(htmlContent, "</p>", "\n\n");
    htmlContent = replaceAll(htmlContent, "</div>", "\n\n");
    htmlContent = replaceAll(htmlContent, "<br>", "\n");
    htmlContent = replaceAll(htmlContent, "<br/>", "\n");
    htmlContent = replaceAll(htmlContent, "<br />", "\n");
    htmlContent = replaceAll(htmlContent, "<h1>", "\n\n");
    htmlContent = replaceAll(htmlContent, "</h1>", "\n\n");
    htmlContent = replaceAll(htmlContent, "<h2>", "\n\n");
    htmlContent = replaceAll(htmlContent, "</h2>", "\n\n");
    htmlContent = replaceAll(htmlContent, "<li>", "\n* ");
    htmlContent = replaceAll(htmlContent, "</li>", "\n");
    htmlContent = replaceAll(htmlContent, "</ul>", "\n");
    htmlContent = replaceAll(htmlContent, "</ol>", "\n");
    htmlContent = replaceAll(htmlContent, "<tr>", "\n");
    log("Repl. HTML block tags.");
    StringBuffer sb = new StringBuffer();
    boolean inTag = false;
    for (int i = 0; i < htmlContent.length(); i++) {
      char c = htmlContent.charAt(i);
      if (c == '<') {
        inTag = true;
      } else if (c == '>') {
        inTag = false;
      } else if (!inTag) {
        sb.append(c);
      }
    }
    String plainText = sb.toString();
    log("Strip. remaining HTML tags.");
    plainText = replaceAll(plainText, "&amp;", "&");
    plainText = replaceAll(plainText, "&lt;", "<");
    plainText = replaceAll(plainText, "&gt;", ">");
    plainText = replaceAll(plainText, "&quot;", "\"");
    plainText = replaceAll(plainText, "&#39;", "'");
    plainText = replaceAll(plainText, "&nbsp;", " ");
    log("Dec. HTML entities.");
    plainText = replaceAll(plainText, "\n\n\n", "\n\n");
    plainText = replaceAll(plainText, "  ", " ");
    log("Clean. newlines/spaces.");
    return plainText.trim();
  }

  private String removeTagsAndContent(String htmlContent, String tagName) {
    String startTag = "<" + tagName;
    String endTag = "</" + tagName + ">";
    StringBuffer sb = new StringBuffer();
    int lastIndex = 0;
    int startIndex = 0;
    log("Rm <" + tagName + "> tags.");
    while ((startIndex = htmlContent.indexOf(startTag, lastIndex)) != -1) {
      sb.append(htmlContent.substring(lastIndex, startIndex));
      int endIndex = htmlContent.indexOf(endTag, startIndex + startTag.length());
      if (endIndex != -1) {
        lastIndex = endIndex + endTag.length();
        continue;
      }
      log("Warn: Miss. " + tagName + " close tag at " + startIndex);
      lastIndex = startIndex + startTag.length();
    }
    sb.append(htmlContent.substring(lastIndex, htmlContent.length()));
    return sb.toString();
  }

  private String removeComments(String htmlContent) {
    StringBuffer sb = new StringBuffer();
    int lastIndex = 0;
    int startIndex = 0;
    log("Rm HTML comments.");
    while ((startIndex = htmlContent.indexOf("<!--", lastIndex)) != -1) {
      sb.append(htmlContent.substring(lastIndex, startIndex));
      int endIndex = htmlContent.indexOf("-->", startIndex + 4);
      if (endIndex != -1) {
        lastIndex = endIndex + "-->".length();
        continue;
      }
      log("Warn: Miss. HTML comment close tag at " + startIndex);
      lastIndex = startIndex + "<!--".length();
    }
    sb.append(htmlContent.substring(lastIndex, htmlContent.length()));
    return sb.toString();
  }

  private String replaceAll(String original, String oldString, String newString) {
    if (original == null || oldString == null || newString == null || oldString.length() == 0)
      return original;
    int index = original.indexOf(oldString);
    if (index == -1)
      return original;
    StringBuffer sb = new StringBuffer(original.length());
    int lastIndex = 0;
    while (index != -1) {
      sb.append(original.substring(lastIndex, index));
      sb.append(newString);
      lastIndex = index + oldString.length();
      index = original.indexOf(oldString, lastIndex);
    }
    sb.append(original.substring(lastIndex, original.length()));
    return sb.toString();
  }

  private String[] splitString(String text, char delimiter) {
    Vector result = new Vector();
    int lastIndex = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == delimiter) {
        result.addElement(text.substring(lastIndex, i));
        lastIndex = i + 1;
      }
    }
    result.addElement(text.substring(lastIndex, text.length()));
    String[] array = new String[result.size()];
    result.copyInto((Object[])array);
    return array;
  }

  private static String decodeEncrypt(String encoded) throws IOException {
    if (encoded == null)
      throw new IOException("Input string is null.");
    StringBuffer cleanedEncoded = new StringBuffer();
    for (int k = 0; k < encoded.length(); k++) {
      char ch = encoded.charAt(k);
      if (ch != ' ' && ch != '\t' && ch != '\n' && ch != '\r')
        cleanedEncoded.append(ch);
    }
    encoded = cleanedEncoded.toString();
    if (encoded.length() % 4 != 0)
      throw new IOException("Invalid Encrypt string length.");
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    int i = 0;
    while (i < encoded.length()) {
      int b1 = Encrypt_DECODE_TABLE[encoded.charAt(i++)];
      int b2 = Encrypt_DECODE_TABLE[encoded.charAt(i++)];
      int b3 = Encrypt_DECODE_TABLE[encoded.charAt(i++)];
      int b4 = Encrypt_DECODE_TABLE[encoded.charAt(i++)];
      if (b1 == -1 || b2 == -1 || b3 == -1 || b4 == -1)
        throw new IOException("Invalid Encrypt character detected.");
      bos.write(b1 << 2 | b2 >> 4);
      if (encoded.charAt(i - 2) != '=') {
        bos.write((b2 & 0xF) << 4 | b3 >> 2);
        if (encoded.charAt(i - 1) != '=')
          bos.write((b3 & 0x3) << 6 | b4);
      }
    }
    return new String(bos.toByteArray(), "UTF-8");
  }
}
