import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
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
  private Display display;
  
  private Form introForm;
  
  private Form mainForm;
  
  private TextField inputField;
  
  private TextField copiedField;
  
  private StringItem responseItem;
  
  private Command startCommand;
  
  private Command sendCommand;
  
  private Command clearCommand;
  
  private Command copyCommand;
  
  private StringItem historyItem;
  
  private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
  
  private static final String API_KEY = "token from openrouter";
  
  private Command toggleHistoryCommand;
  
  private boolean isHistoryVisible = false;
  
  private void initializeHistory() {
    this.historyItem = new StringItem("History:", "");
    //this.mainForm.append((Item)this.historyItem);
  }
  
  public AIChatApp() {
    this.display = Display.getDisplay(this);
    this.introForm = new Form("Welcome to Deepseek Chat");
    Image DeepseekIcon = null;
    try {
      DeepseekIcon = Image.createImage("/deepseek.png");
    } catch (IOException e) {
      e.printStackTrace();
    } 
    if (DeepseekIcon != null) {
      ImageItem DeepseekIconItem = new ImageItem(null, DeepseekIcon, 0, "Deepseek Icon");
      this.introForm.append((Item)DeepseekIconItem);
    } 
    StringItem introText = new StringItem("", "\n AI Chat for J2ME mobile.");
    this.startCommand = new Command("Start", 4, 1);
    this.introForm.append((Item)introText);
    this.introForm.addCommand(this.startCommand);
    this.introForm.setCommandListener(this);
    this.mainForm = new Form("Deepseek Chat");
    this.inputField = new TextField("Message:", "", 256, 0);
    this.copiedField = new TextField("Copied Text:", "", 5000, 0);
    this.responseItem = new StringItem("Response:", "");
    this.sendCommand = new Command("Send", 4, 1);
    this.clearCommand = new Command("Clear", 1, 2);
    this.copyCommand = new Command("Copy", 1, 3);
    this.toggleHistoryCommand = new Command("Show History", Command.OK, 1);
    this.mainForm.append((Item)this.inputField);
    this.mainForm.append((Item)this.responseItem);
    this.mainForm.addCommand(this.sendCommand);
    this.mainForm.addCommand(this.copyCommand);
    this.mainForm.addCommand(toggleHistoryCommand);
    initializeHistory();
    this.mainForm.setCommandListener(this);
  }
  
  private void updateHistory(String userText, String response) {
    String currentHistory = this.historyItem.getText();
    String newEntry = "User: " + userText + "\nResponse: " + response + "\n";
    this.historyItem.setText(newEntry + currentHistory);
  }
  
  protected void startApp() {
    this.display.setCurrent((Displayable)this.introForm);
  }
  
  protected void pauseApp() {}
  
  protected void destroyApp(boolean unconditional) {}
  
  public void commandAction(Command cmd, Displayable disp) {
    if (cmd == this.startCommand) {
      this.display.setCurrent((Displayable)this.mainForm);
    } else if (cmd == this.sendCommand) {
      final String userText = this.inputField.getString().trim();
      this.responseItem.setText("Wait for result...");
      new Thread(new Runnable() {
    
    public void run() {
        String response = generateContentWithDeepseek(userText);
        responseItem.setText("Response: " + response + "\r\n");
        updateHistory(userText, response);
        mainForm.removeCommand(clearCommand);
        mainForm.addCommand(copyCommand);
        display.setCurrentItem(mainForm.get(0));
    }
}).start();

      this.inputField.setString("");
    } else if (cmd == this.clearCommand) {
      this.inputField.setString("");
      this.responseItem.setText("");
      this.copiedField.setString("");
      if (this.mainForm.size() > 0 && this.mainForm.get(0) != this.inputField) {
        this.mainForm.set(0, (Item)this.inputField);
      } else if (this.mainForm.size() == 0) {
        this.mainForm.append((Item)this.inputField);
      } 
      this.mainForm.removeCommand(this.clearCommand);
      this.mainForm.addCommand(this.copyCommand);
      this.mainForm.addCommand(this.sendCommand);
      this.display.setCurrentItem((Item)this.inputField);
    } else if (cmd == this.copyCommand) {
      copyToMainTextBox(this.responseItem.getText());
      this.mainForm.removeCommand(this.copyCommand);
      this.mainForm.addCommand(this.clearCommand);
      this.mainForm.removeCommand(this.sendCommand);
    } else if (cmd == toggleHistoryCommand) {
        isHistoryVisible = !isHistoryVisible;
        updateUI();
    }
  }
  
private void updateUI() {
    mainForm.deleteAll();
    mainForm.removeCommand(toggleHistoryCommand); 
    mainForm.removeCommand(sendCommand); 

    if (isHistoryVisible) {
        mainForm.append(historyItem);
        toggleHistoryCommand = new Command("Hide History", Command.OK, 1);
    } else {
        mainForm.append(inputField);
        mainForm.append(responseItem);
        toggleHistoryCommand = new Command("Show History", Command.OK, 1);
        mainForm.addCommand(sendCommand); 
    }

    mainForm.addCommand(toggleHistoryCommand);
}
  
   private static final String[] MODELS = {
    "example/ai",
    "example/ai2",
    "example/ai3"
    };
  
private String generateContentWithDeepseek(String userText) {
    int attempt = 0;
    
    while (attempt < MODELS.length) {
        String model = MODELS[attempt];
        String response = tryGenerateContentWithModel(userText, model);
        
        if (response != null) {
            return response;
        }
        
        attempt++;
    }

    return "Error: No available model could generate content.";
}
  
  private String tryGenerateContentWithModel(String userText, String model) {
    HttpConnection connection = null;
    OutputStream os = null;
    InputStream is = null;
    StringBuffer response = new StringBuffer();
    try {
      connection = (HttpConnection)Connector.open("https://openrouter.ai/api/v1/chat/completions");
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("Authorization", "Bearer token from openrouter");
      String requestBody = "{\"model\":\"" + model + "\",\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"" + userText + "\"}]}]}";
      os = connection.openOutputStream();
      os.write(requestBody.getBytes());
      os.flush();
      is = connection.openInputStream();
      int ch;
      while ((ch = is.read()) != -1)
        response.append((char)ch); 
      String rawResponse = response.toString();
      int errorIndex = rawResponse.indexOf("\"error\":");
      if (errorIndex != -1)
        return null; 
      String target = "content";
      String replacement = "AI chat";
      StringBuffer cleanedResponse = new StringBuffer();
      int start = 0;
      int end = 0;
      while ((end = rawResponse.indexOf(target, start)) >= 0) {
        cleanedResponse.append(rawResponse.substring(start, end));
        cleanedResponse.append(replacement);
        start = end + target.length();
      } 
      cleanedResponse.append(rawResponse.substring(start));
      rawResponse = cleanedResponse.toString();
      int refusalIndex = rawResponse.indexOf("\"refusal\":");
      if (refusalIndex != -1)
        rawResponse = rawResponse.substring(0, refusalIndex); 
      int modelIndex = rawResponse.indexOf("\"AI chat\":");
      if (modelIndex != -1)
        rawResponse = rawResponse.substring(modelIndex); 
      StringBuffer cleanedResult = new StringBuffer();
      for (int i = 0; i < rawResponse.length(); i++) {
        if (rawResponse.charAt(i) == '\\' && i + 1 < rawResponse.length() && rawResponse.charAt(i + 1) == 'n') {
          cleanedResult.append('\n');
          i++;
        } else if (rawResponse.charAt(i) == '\\' && i + 1 < rawResponse.length() && rawResponse.charAt(i + 1) == '"') {
          cleanedResult.append('"');
          i++;
        } else {
          cleanedResult.append(rawResponse.charAt(i));
        } 
      } 
      return cleanedResult.toString();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    } finally {
      try {
        if (is != null)
          is.close(); 
        if (os != null)
          os.close(); 
        if (connection != null)
          connection.close(); 
      } catch (IOException e) {
        e.printStackTrace();
      } 
    } 
  }
  
  private void copyToMainTextBox(String text) {
    this.copiedField.setString(text);
    if (this.mainForm.size() > 1) {
      this.mainForm.delete(0);
      this.mainForm.insert(0, (Item)this.copiedField);
      this.mainForm.removeCommand(this.sendCommand);
    } 
  }
}
