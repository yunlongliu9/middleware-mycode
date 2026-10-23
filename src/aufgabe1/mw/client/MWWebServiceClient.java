package mw.client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
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
            } finally {
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
}
