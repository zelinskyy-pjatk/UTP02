import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public
   class Server {
   // -- Path to Server Configuration File -- //
   private static String configurationFilePath;
   // -- Server Characteristics (Name, Port, Banned Phrases List) -- //
   private static String serverName;
   private static int serverPort;
   private static List<String> bannedPhrasesList = new ArrayList<>();
   // -- Set for storing all connected Clients -- //
   private static Set<ClientHandleThread> connectedClients = Collections.synchronizedSet(new HashSet<>());

   // -- Executor Service (Thread Pool) -- //
   private ExecutorService threadPool;

   // -- Main Method -- //
   public static void main(String[] args) {
      configurationFilePath = args.length > 0 ? args[0] : "..\\ServerClientApplication\\server_configuration.txt";
      new Server().startServer();
   }

   // -- Server Constructor (reading from configuration file) -- //
   public Server() {
      // -- Reading Server Configuration File -- //
      try {
         File configurationFile = new File(configurationFilePath);
         Scanner fileReader = new Scanner(configurationFile);
         while(fileReader.hasNextLine()){
            String line = fileReader.nextLine();
            String[] lineSplitArray = line.split(": ");
            switch (lineSplitArray[0]) {
               case "Server Name" -> serverName = lineSplitArray[1];
               case "Server Port" -> serverPort = Integer.parseInt(lineSplitArray[1]);
               case "Server Banned Phrases List" ->
                       bannedPhrasesList = Arrays.asList(lineSplitArray[1].replace(",", " ").split("\\s+"));
            }
         }
         fileReader.close();
      } catch (FileNotFoundException e) {
         System.out.println("!Error occurred! *Server configuration file not found*");
         System.exit(-111); // -111 -> error code for file not found error
      }
   }

   public void startServer() {
      System.out.println("Attempting to create a server socket.");

      // -- Initializing Thread Pool -- //
      threadPool = Executors.newCachedThreadPool();

      try (ServerSocket serverSocket = new ServerSocket(serverPort);){
         System.out.println("Server Socket created successfully.");
         while (true) {
            System.out.println("Waiting for new clients to connect.");
            Socket clientSocket = serverSocket.accept();
            System.out.println("New client connected");

            BufferedReader tempIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter tempOut = new PrintWriter(clientSocket.getOutputStream(), true);

            String clientName = tempIn.readLine();

            synchronized (connectedClients) {
               boolean usernameExists = connectedClients.stream().anyMatch(
                       client -> client.getClientName().equals(clientName));
               if(!usernameExists){
                  tempOut.println("Welcome " + clientName + " to the server " + serverName);

                  tempOut.println("Connected Client List:");
                  for (ClientHandleThread client : connectedClients)
                     tempOut.println("Client: " + client.getClientName());

                  tempOut.println("      Instructions on how to use our server-client application:      \n" +
                          "  * To send a message to everyone (all connected clients) -> type YOUR_MESSAGE and press Enter.\n" +
                          "  * To send a message to a specific person(client) -> type: @username YOUR_MESSAGE\n" +
                          "  * To send a message to multiple people(clients) -> type: @username1,username2 YOUR_MESSAGE\n" +
                          "  * To send a message to everyone except certain people(clients) -> type: @exceptions{username1,username2} YOUR_MESSAGE\n" +
                          "  * To request the list of banned phrases -> type: @ListOfBannedPhrases\n" +
                          "  * To exit -> type: /exit");

                  ClientHandleThread clientHandleThread = new ClientHandleThread(clientSocket, this, clientName);
                  connectedClients.add(clientHandleThread);
                  // -- Submit Client Handle Thread to the Thread Pool -- //
                  threadPool.execute(clientHandleThread);

                  broadcastMessage(clientName + " has joined the server.", clientHandleThread);
               } else {
                  tempOut.println("Client " + clientName + " already exists. Disconnecting...");
                  clientSocket.close();
               }
            }
         }
      } catch (IOException e) {
         e.printStackTrace();
         System.exit(-1);
      } finally {
         // -- Shutdown the Thread Pool -- //
         if(threadPool != null && !threadPool.isShutdown()) threadPool.shutdown();
      }
   }

   public static void broadcastMessage(String msg, ClientHandleThread sender) {
      if(msg == null || msg.trim().isEmpty()) return;

      if(sender != null && containsBannedPhrase(msg)){
         sender.sendMessage("*Server* -> Your message was not sent because it contains a banned phrase.");
         return;
      }

      if(msg.equalsIgnoreCase("@ListOfBannedPhrases")) {
         if (sender != null) sender.sendMessage("*Server* -> Banned Phrases: " + String.join(", ", bannedPhrasesList));
         return;
      }

      if(msg.startsWith("@exceptions{")){
         sendMessageEveryoneWithExceptions(msg, sender);
         return;
      } else if (msg.startsWith("@")){
         int splitIndex = msg.indexOf(" ");
         if(splitIndex > 1) {
            String privateTargetClientUsername = msg.substring(1, splitIndex).trim();
            String privateMessage = msg.substring(splitIndex + 1).trim();

            if(privateTargetClientUsername.contains(",")){
               sendMessageToMultipleClients(privateTargetClientUsername, privateMessage, sender);
            } else {
               sendPrivateMessage(privateTargetClientUsername, privateMessage, sender);
            }
         } else {
            if(sender != null)
               sender.sendMessage("*Server* -> Invalid Message Format.");
         }
      } else {
         sendMessagetoAll(msg, sender);
      }
   }
   // -- Send Message to All Connected Clients Method -- //
   private static void sendMessagetoAll(String msg, ClientHandleThread sender) {
      String forwardedMessage = sender != null ? sender.clientName + ": " + msg.trim() : msg.trim();
      synchronized (connectedClients) {
         for (ClientHandleThread client : connectedClients) {
            if(client != sender){
               client.sendMessage(forwardedMessage);
            }
         }
      }
   }
   // -- Send Private Message (only to one specific Client) -- //
   private static void sendPrivateMessage(String targetUsername, String msg, ClientHandleThread sender) {
      boolean found = false;

      synchronized (connectedClients) {
         for (ClientHandleThread client : connectedClients) {
            if(client != sender && client.clientName.equals(targetUsername)) {
               client.sendMessage("(Private Message) " + sender.clientName + ":" + msg);
               found = true;
               break;
            }
         }
      }
      if(found) {
         sender.sendMessage("(Private Message forwarded to " + targetUsername + ")" + ": " + msg);
      } else sender.sendMessage("*Server* -> User @" + targetUsername + " not found.");
   }
   // -- Send Message to Multiple Clients -- //
   private static void sendMessageToMultipleClients(String recipients, String msg, ClientHandleThread sender) {
      String[] recipientsUsernames = recipients.split(",");
      Set<String> recipientsUsernameSet = new HashSet<>();

      for(String username : recipientsUsernames){
         recipientsUsernameSet.add(username.trim());
      }

      boolean foundAnyRecipient = false;
      //TODO: -- FOR LOOP ADDED -- //
      synchronized (connectedClients) {
         for (ClientHandleThread client : connectedClients) {
            for (String username : recipientsUsernameSet) {
               if(client != sender && client.clientName.equals(username)) {
                  client.sendMessage("(Group Message) " + sender.clientName + ": " + msg);
                  foundAnyRecipient = true;
               }
            }
         }
      }

      if(foundAnyRecipient) sender.sendMessage("(Group Message) " + msg + " was sent.");
      else sender.sendMessage("*Server* ->  None of the specified users were found.");
   }
   // -- Send Message to Everyone (with exceptions) -- //
   private static void sendMessageEveryoneWithExceptions(String msg, ClientHandleThread sender) {
      int startOfExceptionsList = msg.indexOf("{");
      int endOfExceptionsList = msg.lastIndexOf("}");
      if(startOfExceptionsList == -1 || endOfExceptionsList == -1 || endOfExceptionsList <= startOfExceptionsList + 1) {
         sender.sendMessage("*Server* -> Invalid @exceptions specifier use. Correct usage: @exceptions {CLIENT_USERNAME1, CLIENT_USERNAME2, ...} Your message.");
         return;
      }

      String exceptionsList = msg.substring(startOfExceptionsList + 1, endOfExceptionsList);
      String[] exceptionsUsernamesList = exceptionsList.split(",");
      Set<String> excludedUsernames = new HashSet<>();
      for (String username : exceptionsUsernamesList)
         excludedUsernames.add(username.trim());


      String forwardedMessage = msg.substring(endOfExceptionsList + 1).trim();
      if(forwardedMessage.isEmpty()){
         sender.sendMessage("*Server* -> Forwarded message is empty. Try sending again.");
         return;
      }

      synchronized (connectedClients) {
         for (ClientHandleThread client : connectedClients) {
            if(!excludedUsernames.contains(client.clientName) && client != sender){
               client.sendMessage(sender.clientName  + " : " + forwardedMessage);
            }
         }
      }
      sender.sendMessage("(Message sent to all excluding specified users) " + forwardedMessage);
   }

   // -- Check if the message contains Banned Phrases -- //
   public static boolean containsBannedPhrase(String msg){
      for (String bannedPhrase : bannedPhrasesList){
         if(msg.toLowerCase().contains(bannedPhrase.toLowerCase())){
            return true;
         }
      }
      return false;
   }

   // -- Remove Client Method -- //
   public static void removeClient(ClientHandleThread clientHandleThread) {
      connectedClients.remove(clientHandleThread);
      broadcastMessage(clientHandleThread.clientName + " has disconnected from the server.", null);
   }

   // -- Static Class ClientHandleThread for each client to be another thread -- //
   private static class ClientHandleThread implements Runnable {
      private Socket clientSocket;
      private Server server;
      private String clientName;
      private PrintWriter out;
      private BufferedReader in;

      // -- Client Handle Thread Constructor -- //
      public ClientHandleThread(Socket clientSocket, Server server, String clientName) {
         this.clientSocket = clientSocket;
         this.server = server;
         this.clientName = clientName;
      }

      @Override
      public void run() {
         try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            // -- Sending Client Message -- //
            String clientMsg;
            while((clientMsg = in.readLine()) != null) {
               if(clientMsg.contains("/exit")) {
                  break;
               }

               String cleanMsg = clientMsg.trim();
               if(!cleanMsg.isEmpty())
                  server.broadcastMessage(cleanMsg, this);
            }
         } catch (IOException e){
            System.out.println("Connection with client " + clientName + " lost.");
         } finally {
            closeConnection();
         }
      }

      // -- Basic Send Message Method -- //
      public void sendMessage(String msg){
         if(out != null && msg != null && !msg.trim().isEmpty())
            out.println(msg.trim());
         else System.err.println("The message is empty!");
      }
      // -- Getting Client Name from Client Handle Thread -- //
      public String getClientName(){return clientName;}
      // -- Close All Connections (communication streams and user input readers, remove client from connected clients list) -- //
      public void closeConnection(){
         try {
            if(in != null) in.close();
            if(out != null) out.close();
            if(clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
         } catch (IOException e) {
            System.err.println("Error closing connection with " + clientName);
         }
         if(clientName != null){
            server.removeClient(this);
         }
      }
   }
}
