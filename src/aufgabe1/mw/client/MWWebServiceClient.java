package mw.client;
import java.util.HashSet;
import java.util.Map;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import mw.path.MWPath;
import javax.ws.rs.core.Response;
public class MWWebServiceClient extends MWShell {
    private MWRegistryClient regClient;
    private String url=null;
    private String groupName = "i4";
    private String serviceName = "facebook";


    private WebTarget client;
    public MWWebServiceClient(){
        regClient = new MWRegistryClient();
        try {
            url = regClient.getValue(groupName, serviceName, "address");
            this.client = ClientBuilder.newClient().target(url);
        } catch (MWWebServiceException e) {
            throw new RuntimeException("Failed to get address from registry", e);
        }
    }

    public String[] search(String string) throws MWWebServiceException{
        if (this.client == null) {
            throw new MWWebServiceException("Client is not successfully initialized");
        }
        Response response = this.client.path("search").queryParam("string", string).request().get();
        int statusCode = response.getStatus();
        if (statusCode == 200){
            String[] result = response.readEntity(String[].class);
            response.close();
            return result;
        }else{
            try {
                checkResponse(statusCode);
                return null;
            }
            finally {
                response.close();
            }
        }
    }

    public String getName(String id) throws MWWebServiceException {
        if (this.client == null) {
            throw new MWWebServiceException("Client is not successfully initialized");
        }
        Response response = this.client.path("names").path(id).request().get();
        int statusCode = response.getStatus();
        if (statusCode == 200) {
            String name = response.readEntity(String.class);
            response.close();
            return name;
        } else {
            try {
                checkResponse(statusCode);
                return null;
            } finally {
                response.close();
            }
        }
    }

    public String[] getFriends(String id) throws MWWebServiceException {
        if (this.client == null) {
            throw new MWWebServiceException("Client is not successfully initialized");
        }
        Response response = this.client.path("friends").path(id).request().get();
        int statusCode = response.getStatus();
        if (statusCode == 200) {
            String[] friends = response.readEntity(String[].class);
            response.close();
            return friends;
        } else {
            try {
                checkResponse(statusCode);
                return null;
            }finally {
                response.close();
            }
        }
    }

    public MWPath getPath(String startId, String endId) throws MWWebServiceException {
        WebTarget pathClient = ClientBuilder.newClient()
                .target("http://localhost:8080");

        if (pathClient == null) {
            throw new MWWebServiceException("Client is not successfully initialized");
        }
        Response response = pathClient
                        .path("path")
                        .path(startId)
                        .path(endId).queryParam("batching", true).request().get();
        int statusCode = response.getStatus();
        if (statusCode == 200) {
            MWPath path = response.readEntity(MWPath.class);
            response.close();
            return path;
        } else {
            try {
                checkResponse(statusCode);
                return null;
            } finally {
                response.close();
            }
        }

    }

    public static void main(String[] args) throws MWWebServiceException {
        MWWebServiceClient client = new MWWebServiceClient();
        client.shell();
    }


	@Override
    protected boolean processCommand(String[] args) throws MWWebServiceException {
        switch (args[0]) {
            case "help":
            case "h":
                System.out.println("The following commands are available:\n"
                        + "  help\n"
                        + "  search <string>\n"
                        + "  friends <id>\n"
                        + "  path <startID> <endID>\n"
                        + "  quit");
                break;
            case "search":
                if (args.length < 2) {
                    System.out.println("Usage: search <string>");
                } else {
                    String[] ids = this.search(args[1]);
                    System.out.println("Search results:");
                    for (String id : ids) {
                        String name = this.getName(id);
                        System.out.println("  " + id + " (" + name + ")");
                    }
                }
                break;
            case "friends":
                if (args.length < 2) {
                    System.out.println("Usage: friends <id>");
                } else {
                    String[] friendsIds = this.getFriends(args[1]);
                    for (String id : friendsIds) {
                        String name = this.getName(id);
                        System.out.println("  " + id + " (" + name + ")");
                    }
                }
                break;
            case "path":
                if (args.length < 3) {
                    System.out.println("Usage: path <startID> <endID>");
                } else {
                    MWPath result = this.getPath(args[1], args[2]);

                    System.out.println("Shortest path:");

                    for (String id : result.path) {
                        String name = this.getName(id);
                        System.out.println("  " + id + " (" + name + ")");
                    }

                    System.out.println("Number of IDs: " + result.numberOfIDs);
                    System.out.println("Number of calls: " + result.numberOfCalls);
                }
                break;
            case "exit":
            case "quit":
            case "x":
            case "q":
                return false;
            default:
                throw new IllegalArgumentException(
                        "Unknown command: " + args[0] + "\nUse \"help\" to list available commands");
        }
        return true;
    }

    
    private void checkResponse(int statusCode) throws MWWebServiceException {
        String reason;

        if (statusCode == 400) {
            reason = "Bad Request";
        } else if (statusCode == 401) {
            reason = "Invalid username or password";
        } else if (statusCode == 403) {
            reason = "Permission denied";
        } else if (statusCode == 404) {
            reason = "Resource not found";
        } else if (statusCode == 413) {
            reason = "Payload too large";
        } else if (statusCode >= 500) {
            reason = "Server error";
        } else {
            reason = "Unexpected HTTP response";
        }

        throw new MWWebServiceException(
                reason + " (HTTP " + statusCode + ")");
    }

    public Map<String, HashSet<String>> getFriends(String[] ids) throws MWWebServiceException{
        Response response = this.client.path("friends").request(MediaType.APPLICATION_JSON).post(Entity.json(ids));
        int statusCode = response.getStatus();
        if (statusCode == 200){
            Map<String, HashSet<String>> friends = response.readEntity( new GenericType<Map<String, HashSet<String>>>() {});
            response.close();
            return friends;
        }else{
             try {
                checkResponse(statusCode);
                return null;
            } finally {
                response.close();       
            }
        }
    }


    // /facebook/names
    public String[] getNames(String[] ids) throws MWWebServiceException {
        Response response = this.client.path("names").request(MediaType.APPLICATION_JSON).post(Entity.json(ids));
        int statusCode = response.getStatus();
        if (statusCode == 200) {
            String[] names = response.readEntity(String[].class);
            response.close();
            return names;
        } else {
            try {
                checkResponse(statusCode);
                return null;
            } finally {
                response.close();       
            }
        }   
    }
}
