package org.qo.tinymsg;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.qo.tinymsg.Plugins.PluginLoader;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final ExecutorService executorService;
    public static boolean AllowPlugin;
    public static final String CONFIG_FILE = "config_server.json";
    public static final String USER_PROFILE = "users.json";
    public static final String LOG_FILE = "logs.log";
    public static final String PLUGIN_PROFILE = "plugin_config.json";
    public static final String API_LIST = "apilist.json";
    public static String ServerVersion;
    public int port;
    public String workingDirectory;
    private String srvmsg;
    public final List<ClientHandler> clients;
    public final List<String> onlineUsers;
    private String accesstoken;
    public final List<String> onlineApi;
    public boolean NOPIC;
    public boolean NOTOKEN;
    public int OnlineCount = 0;
    public boolean AllowRegister;
    public boolean noUpdate;
    public static double innerVersion = 17;
    public Server() {
        loadConfig();
        onlineApi = new ArrayList<>();
        clients = new ArrayList<>();
        onlineUsers = new ArrayList<>();
        ServerVersion = "Release 1.4";
        executorService = Executors.newFixedThreadPool(10);
    }

    public String getAccessToken() {
        return accesstoken;
    }
    private void loadConfig() {
        // Load server configuration
        if (fileExists()) {
            // If the configuration file exists, read the configuration
            try {
                String configContent = readFile(CONFIG_FILE);
                String pluginContent = readFile(PLUGIN_PROFILE);
                if (configContent != null) {
                    JSONObject jsonConfig = new JSONObject(configContent);
                    port = jsonConfig.getInt("port");
                    workingDirectory = jsonConfig.getString("workingDirectory");
                    srvmsg = jsonConfig.getString("srvmsg");
                    accesstoken = jsonConfig.getString("token");
                    NOTOKEN = jsonConfig.getBoolean("NOTOKEN");
                    NOPIC = jsonConfig.getBoolean("NOPIC");
                    AllowRegister = jsonConfig.getBoolean("AllowRegister");
                    noUpdate = jsonConfig.getBoolean("noUpdate");
                }
                if (pluginContent != null) {
                    JSONObject pluginconfig = new JSONObject(pluginContent);
                    AllowPlugin = pluginconfig.getBoolean("EnablePlugin");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            // Configuration file doesn't exist, use default configuration and generate the configuration file
            port = 1234;
            workingDirectory = System.getProperty("user.dir");
            srvmsg = "CONNECT SUCCESS";
            createDefaultConfig();
        }
    }

    private boolean fileExists() {
        File file = new File(Server.CONFIG_FILE);
        return file.exists() && !file.isDirectory();
    }

    private void createDefaultConfig() {
        try {
            JSONObject jsonConfig = new JSONObject();
            jsonConfig.put("port", port);
            jsonConfig.put("workingDirectory", workingDirectory);
            jsonConfig.put("srvmsg", srvmsg);
            jsonConfig.put("token", TokenGenerate());
            jsonConfig.put("NOPIC", false);
            jsonConfig.put("NOTOKEN", false);
            jsonConfig.put("noUpdate", false);

            writeFile(CONFIG_FILE, jsonConfig.toString());
            JSONObject plugincfg = new JSONObject();
            jsonConfig.put("EnablePlugin", false);
            writeFile(PLUGIN_PROFILE, plugincfg.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String readFile(String filename) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filename));
            StringBuilder content = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                content.append(line);
            }

            reader.close();

            return content.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void writeFile(String filename, String content) {
        try {
            FileWriter writer = new FileWriter(filename);
            writer.write(content);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void log(String input, int level) {
        if (level == 0) {
            System.out.println(ColorfulText.GREEN + input + ColorfulText.RESET);
            //log level: INFO
        } else if (level == 1) {
            System.out.println(ColorfulText.YELLOW + input + ColorfulText.RESET);
            //log level: WARNING
        } else if (level == 2) {
            System.out.println(ColorfulText.RED + input + ColorfulText.RESET);
            //log level: ERROR
        } else if (level == 3) {
            System.out.println(ColorfulText.CYAN + input + ColorfulText.RESET);
        } else {
            log("Your specfied log level is invalid. Please refactor your code.", 2);
        }
    }
    public void start() throws Exception {
        System.out.println(ColorfulText.LOGO);
        //Print an ASCLL STYLE logo.
        if (AllowPlugin)
        {
            PluginLoader.startup();
        }
        Debugger debugger = new Debugger();
        if (debugger.needUpdate()) {
            if (!noUpdate) {
                log("You are now running outdated version of TinyMSG!", 2);
            }
        }
        try {
            ApiControler.start();
            Logger.startup();
            // Create ServerSocket object and bind it to the listening port
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            ServerSocket serverSocket = new ServerSocket(port);
            new Thread(new ConsoleCommandHandler(this)).start();
            // Display server startup message
            System.out.println("[start] TinyMSG Server " + ServerVersion + " Started! Bind at " + port + " port");
            String os = System.getProperty("os.name");
            if (os.equals("Linux")) {
                log("You are now running TinyMSG on Operating System based on Linux. To enable ALL features of TinyMSG, please run it under SUDO mode.", 0);
            } else {
                log("Your Operating System is: " + os, 0);
                log("Your are now running TinyMSG on Windows or MacOS(anyway) please ensure that you gave TinyMSG complete permission to Read and Write files.", 0);
            }

            while (true) {
                // Listen for client connection requests
                Socket clientSocket = serverSocket.accept();

                // Create a new client handler thread
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);

                // Start the thread to handle the client connection
                executorService.execute(clientHandler);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void broadcastMessage(String message) {
        if (clients != null) {
            broadcastMessage(message, "ALL");
        }
    }

    private void broadcastMessage(String message, String specify) {
        // Broadcast the message to all connected clients
        for (ClientHandler client : clients) {
            Logger.log(message);
            if (specify.contentEquals("ALL") || specify.contentEquals(client.username)) client.sendMessage(message);
        }
    }

    private class ClientHandler extends Thread {
        private final Socket clientSocket;
        private PrintWriter out;
        public String username;

        public ClientHandler(Socket socket) {
            clientSocket = socket;
        }

        private void disconnectClient(boolean doBroadcast) {
            if (doBroadcast) {
                broadcastMessage("[server] user " + username + " disconnected.");
                log("[server] user " + username + " disconnected.",0);
            }
            OnlineCount--;
            clients.remove(this);
            onlineUsers.remove(username);

            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                //empty catch block
            }
        }

        public void run() {
            try {
                // Get input and output streams for the client connection
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Prompt for username

                // Receive the username from the client
                username = in.readLine();
                // Prompt for password

                // Receive the password from the client
                String password = in.readLine();
                String unverifytoken = in.readLine();
                if ((Objects.equals(unverifytoken, accesstoken) || NOTOKEN)) {
                    if (verifyCredentials(username, password))
                    {
                        // Check if the user is already online
                        if (isUserOnline(username)) {
                            out.println("[ERROR] User is already logged in. Disconnected.");
                            return;
                        }
                        if (ApiControler.isServer(username)) {
                            onlineApi.add(username);
                            out.println("SUCCESS CONNECTED TO SERVER");
                            log("Api " + username + " connected.", 1);
                        } else {
                            onlineUsers.add(username);
                            OnlineCount++;
                            out.println(srvmsg);
                            // Add the user to the online users list
                        }


                        // Send server message to the client
                    } else {
                        if (!AllowRegister) {
                            out.println("[ERROR] Invalid username or password. Disconnected.");
                            disconnectClient(false);
                            return;
                        } else {
                            out.println("[server] Invalid username. " + "Do you want register a new user? (y/n)");
                            String choice = in.readLine();
                            if (choice.equals("y")) {
                                out.println("Please enter your username.");
                                String unregUsername = in.readLine();
                                out.println("Please enter your password.");
                                String unregPassword1 = in.readLine();
                                out.println("Please retype your password.");
                                String unregPassword2 = in.readLine();
                                if (Objects.equals(unregPassword1, unregPassword2)) {
                                    String uname = unregUsername;
                                    String pswd = unregPassword1;
                                    if (Register.reg(uname, pswd)){
                                        out.println("Register complete. please reconnect to login.");
                                        disconnectClient(false);
                                        return;
                                    } else if(!Register.reg(uname, pswd)) {
                                        out.println("Failed to register.");
                                        disconnectClient(false);
                                    }
                                }
                            } else {
                                disconnectClient(false);
                                return;
                            }
                        }
                    }
                } else {
                    out.println("[ERROR] Wrong token! If you believe this is an error, please contact with your server administrator.");
                    log("[server] user " + username + " disconnected. reason: wrong token", 1);
                    disconnectClient(false);
                }
                // Verify the username and password

                while (true) {
                    // Receive client messages
                    String clientMessage = in.readLine();

                    if (clientMessage.equalsIgnoreCase("/version")) {
                        out.println("[server] TinyMSG server version " + ServerVersion);
                        clientMessage = "";
                    }
                    if (clientMessage.equalsIgnoreCase("/help")) {
                        out.println("[server] TinyMSG server version " + ServerVersion);
                        out.println("[server] /exit to Disconnect");
                        out.println("[server] /version to show Server Version");
                        out.println("[server] /list to show online users");
                        out.println("[server] /permission to show your permission level");
                        out.println("[server] /stop to stop the server");
                        clientMessage = "";
                    }
                    if (clientMessage.equalsIgnoreCase("/list")) {
                        // Send the current online user list to the client
                        sendOnlineUsersList();
                        clientMessage = "";
                    }
                    if (clientMessage.equalsIgnoreCase("/permission")) {
                        // Send the user's permission level to the client
                        String permissionLevel = showPermission(username);
                        out.println("[server] Your permission level: " + permissionLevel);
                        clientMessage = "";
                    }
                    if (clientMessage.equalsIgnoreCase("/token")) {
                        if (NOTOKEN) {
                            log("this server isn't opened token verify. please enable it in server config.", 1);
                        }else {
                            broadcastMessage("[server] server token is " + accesstoken);
                            clientMessage = "";
                        }
                    }
                    if (clientMessage.equalsIgnoreCase("/exit")) {
                        disconnectClient(true);
                        clientMessage = "";
                    }
                    if (clientMessage.equalsIgnoreCase("/stop")) {
                        if (isAdmin(username)) {

                            System.exit(0);
                        } else {
                            broadcastMessage("You don't have permission to DO THAT!", username);
                            clientMessage = "";
                        }
                    }

                    if (clientMessage == null) {
                        // Client has disconnected
                        break;
                    }

                    if (clientMessage != null && !clientMessage.isEmpty()) {
                        if (onlineApi.contains(username)) {
                            System.out.println("[API] [" + username + "] " + clientMessage);
                            broadcastMessage("[API] [" + username + "] " + clientMessage);
                            Logger.log("[API] [" + username + "] " + clientMessage);
                        } else {
                            // Display the received message on the server console
                            System.out.println("[Client:" + username + "] " + clientMessage);
                            Logger.log("[Client:" + username + "] " + clientMessage);
                            // Broadcast the message to all connected clients
                            broadcastMessage("[Client:" + username + "] " + clientMessage);
                        }
                    }

                }

                disconnectClient(false);
            } catch (IOException e) {
                //empty catch block
                disconnectClient(true);
            }

        }

        private boolean isAdmin(String username) {
            try {
                String userContent = readFile(USER_PROFILE);
                if (userContent != null) {
                    JSONObject userProfiles = new JSONObject(userContent);
                    if (userProfiles.has(username)) {
                        JSONObject userProfile = userProfiles.getJSONObject(username);
                        int permissionlvl = userProfile.getInt("permission");
                        if (Objects.equals(permissionlvl, 1)) {
                            return true;
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return false;
        }
        public boolean ExistsUser(){
            try {
                String userContent = readFile(USER_PROFILE);
                if (userContent != null) {
                    JSONObject userProfiles = new JSONObject(userContent);
                    if (userProfiles.has(username)) {
                        JSONObject userProfile = userProfiles.getJSONObject(username);
                        return true;
                    }
                }
            } catch (JSONException e){
                e.printStackTrace();
            }
            return false;
        }

        private void sendMessage(String message) {
            // Send a message to the client
            out.println(message);
        }
    }

    private boolean verifyCredentials(String username, String password) {
        try {
            String userContent = readFile(USER_PROFILE);
            String ApiContent = readFile(API_LIST);
            if (userContent != null) {
                JSONObject userProfiles = new JSONObject(userContent);
                JSONObject apiContent = new JSONObject(ApiContent);
                if (userProfiles.has(username)) {
                    JSONObject userProfile = userProfiles.getJSONObject(username);
                    String storedPassword = userProfile.getString("password");
                    return password.equals(storedPassword);
                } else if (apiContent.has(username)) {
                    JSONObject apicnt = apiContent.getJSONObject(username);
                    JSONObject apipswd = apiContent.getJSONObject("password");
                    return password.equals(apipswd);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    private String showPermission(String username) {
        try {
            String userContent = readFile(USER_PROFILE);
            if (userContent != null) {
                JSONObject userProfiles = new JSONObject(userContent);
                if (userProfiles.has(username)) {
                    JSONObject userProfile = userProfiles.getJSONObject(username);
                    int userPermissionLevel = userProfile.getInt("permission");
                    if (userPermissionLevel == 1) {
                        String userPermissionReturn = "admin";
                        return String.valueOf(userPermissionReturn);
                    }
                    if (userPermissionLevel == 0) {
                        String userPermissionReturn = "user";
                        return String.valueOf(userPermissionReturn);
                    }
                    if (userPermissionLevel == 2) {
                        String userPermissionReturn = "System";
                        return String.valueOf(userPermissionReturn);
                    } else {
                        broadcastMessage("[ERROR] Illegal Argument: Permission in user:" + username);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return username;
    }

    private boolean isUserOnline(String username) {
        return onlineUsers.contains(username);
    }

    private void sendOnlineUsersList() {
        StringBuilder userList = new StringBuilder();
        for (String user : onlineUsers) {
            userList.append("\n").append(user);
        }
        broadcastMessage("[server] Online Users:" + userList + "\n" + "Online count: " + OnlineCount);
    }

    public static void main(String[] args) throws Exception {
        Server server = new Server();
        server.start();
    }

    private @NotNull String TokenGenerate() {
        int length = 256;
        String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(62);
            sb.append(str.charAt(number));
        }
        broadcastMessage(sb.toString());
        String token = sb.toString();
        return sb.toString();
    }

    public boolean addUser(String username, String password) {
        try {
            String userContent = readFile(USER_PROFILE);
            JSONObject userProfiles;
            if (userContent != null) {
                userProfiles = new JSONObject(userContent);
            } else {
                userProfiles = new JSONObject();
            }

            if (userProfiles.has(username)) {
                log("[ERROR] User already exists.", 1);
                return false;
            }

            JSONObject newUserProfile = new JSONObject();
            newUserProfile.put("password", password);
            newUserProfile.put("permission", 0); // 设置默认权限为0

            userProfiles.put(username, newUserProfile);

            writeFile(USER_PROFILE, userProfiles.toString()); // 将更新后的JSON写回到文件中
            System.out.println("[server] User " + username + " added.");
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }
    public boolean addAdmin(String username, String password) {
        try {
            String userContent = readFile(USER_PROFILE);
            JSONObject userProfiles;
            if (userContent != null) {
                userProfiles = new JSONObject(userContent);
            } else {
                userProfiles = new JSONObject();
            }

            if (userProfiles.has(username)) {
                log("[ERROR] User already exists.", 1);
                return false;
            }

            JSONObject newUserProfile = new JSONObject();
            newUserProfile.put("password", password);
            newUserProfile.put("permission", 1); // 设置默认权限为0

            userProfiles.put(username, newUserProfile);

            writeFile(USER_PROFILE, userProfiles.toString()); // 将更新后的JSON写回到文件中
            System.out.println("[server] User " + username + " added with Admin permission.");
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }
    public boolean delUser(String username) {
        try {
            String userContent = readFile(USER_PROFILE);
            JSONObject userProfiles;
            if (userContent != null) {
                userProfiles = new JSONObject(userContent);
            } else {
                log("[ERROR] User profile file not found.", 2);
                return false;
            }

            if (userProfiles.has(username)) {
                userProfiles.remove(username);
                writeFile(USER_PROFILE, userProfiles.toString()); // 保存更新后的用户配置文件
                log("[server] User " + username + " deleted.", 0);
                return true;
            } else {
                log("[ERROR] User not found.", 1);
                return false;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }
}
