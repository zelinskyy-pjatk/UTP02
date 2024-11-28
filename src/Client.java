import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Client {
   // -- Client Communication Streams -- //
   private static PrintWriter out = null;
   private static BufferedReader in = null;
   private static BufferedReader stdIn = null;

   // -- Server Hostname and Port -- //
   private static String hostname;
   private static int port;

   // -- Client Name -- //
   private static String clientName;

   // -- Executor Service (Thread Pool) -- //
   private static ExecutorService threadPool;

   public static void main(String[] args) throws IOException {
      if(args.length == 0) throw new IOException("You should specify your client's name as argument (e.g. java Client NICKNAME) OR your client's name, hostname, and port number as argument (e.g. java Client NICKNAME HOSTNAME PORT_NUMBER");
      else if(args.length == 1) {
         clientName = args[0];
         hostname = "192.168.0.171"; // -- Default Hostname of our Server -- //
         port = 12451; // -- Default Port of our Server -- //
      }
      else if(args.length == 3) {
         clientName = args[0];
         hostname = args[1];
         try {
            port = Integer.parseInt(args[2]);
         } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port number must be a valid integer.");
         }
      } else {
         throw new IllegalArgumentException("Invalid arguments! Provide the client's name (required) and optionally hostname and port (e.g., java Client NICKNAME or java Client NICKNAME HOSTNAME PORT_NUMBER).");
      }

      System.out.println("Trying to connect to " + hostname);
      threadPool = Executors.newCachedThreadPool();

      try (Socket socket = new Socket(hostname, port)) {
         out = new PrintWriter(socket.getOutputStream(), true);
         in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
         stdIn = new BufferedReader(new InputStreamReader(System.in));

         // -- Send client name to server -- //
         out.println(clientName);

         // -- Start a thread for listening to incoming messages -- //
         threadPool.submit(() -> {
            try {
               String serverMsg;
               while ((serverMsg = in.readLine()) != null) {
                  System.out.println(serverMsg);
               }
            } catch (IOException e) {
               System.err.println("Disconnected from server.");
            }
         });

         // -- Sending User Input Messages -- //
         String userInput;
         while ((userInput = stdIn.readLine()) != null) {
            if(userInput.trim().isEmpty()){
               System.out.println("Empty messages are not allowed.");
               continue;
            }
            if (userInput.equalsIgnoreCase("/exit")) {
               out.println("/exit");
               break;
            }
            out.println(userInput.trim());
         }
      } catch (IOException e) {
         System.err.println("Error connecting to server: " + e.getMessage());
      } finally {
         try {
            // -- Shutdown Thread Pool -- //
            if(threadPool != null && !threadPool.isShutdown()) threadPool.shutdown();
            // -- Close All Communication and Reading Streams -- //
            if(stdIn != null) stdIn.close();
            if(out != null) out.close();
            if(in != null) in.close();
         } catch (IOException e) {
            System.err.println(e.getMessage());
            System.err.println("Error closing input stream: " + e.getMessage());
         }
      }
   }
}
