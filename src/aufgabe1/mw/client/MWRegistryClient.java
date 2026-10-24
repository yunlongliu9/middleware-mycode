package mw.client;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;

public class MWRegistryClient extends MWShell {

	private WebTarget client;
	private String RegistryURL;
	private HttpAuthenticationFeature auth;
	private final String username = "ja10fylo";
	private final String password = "mcyuvretqnvf";

	public MWRegistryClient() {
		try {
			auth = HttpAuthenticationFeature.basic(this.username, this.password);
			RegistryURL = Files.readString(Path.of("misc/aufgabe1/registry.address")).trim();
		} catch (Exception e) {
			throw new RuntimeException("Failed to read registry address", e);
		}
		this.client = ClientBuilder.newClient().target(RegistryURL);
		this.client.register(auth);
	}

	// ##########
	// # GROUPS #
	// ##########

	public String[] listGroups() throws MWWebServiceException {
		if (this.client == null) {
			throw new MWWebServiceException("Client is not successfully initialized");
		}

		Response response = this.client.request().get();
		int statusCode = response.getStatus();
		if ( statusCode == 200){
			String[] groups = response.readEntity(String[].class);
			response.close();
			return groups;
		} else {
			String errorMessage = response.readEntity(String.class);
			response.close();
			throw new MWWebServiceException(
					"Failed to list groups: HTTP " + statusCode + " - " + errorMessage);
		}
	}


	// ############
	// # SERVICES #
	// ############

	public String[] listServices(String group) throws MWWebServiceException {
		if (this.client == null) {
			throw new MWWebServiceException("Client is not successfully initialized");
		}
		Response response = this.client.path(group).request().get();
		int statusCode = response.getStatus();
		if (statusCode == 200){
			String[] services = response.readEntity(String[].class);
			response.close();
			return services;
		}else{
			try {
				checkResponse(statusCode);
				return null;
			} finally {
				response.close();
			}
		}
	}


	public void createService(String group, String service) throws MWWebServiceException {
		Response response = this.client.path(group).path(service).request().put(Entity.text(""));
		int statusCode = response.getStatus();
		if (statusCode == 200){
			response.close();
		}else {
			try{
				checkResponse(statusCode);
			}finally{
				response.close();
			}
		}
	}



	public void deleteService(String group, String service) throws MWWebServiceException {
		Response response = this.client.path(group).path(service).request().delete();
		int statusCode = response.getStatus();
		if (statusCode == 200){
			response.close();
		}else{
			try {
				checkResponse(statusCode);
			} finally {
				response.close();
			}
		}
	}


	// ###########
	// # ENTRIES #
	// ###########



	public String[] listKeys(String group, String service) throws MWWebServiceException {
		Response response = (this.client.path(group).path(service)).request().get();
		int statusCode = response.getStatus();
		if (statusCode == 200){
			String[] keys = response.readEntity(String[].class);
			response.close();
			return keys;
		}else {
			try {
				checkResponse(statusCode);
				return null;
			} finally {
				response.close();
			}
		}
	}

	// probably cache here,very slow 1st req;
	public String getValue(String group, String service, String key) throws MWWebServiceException {
		Response response = this.client
						.path(group)
						.path(service)
						.path(key)
						.request().get();
		int statusCode = response.getStatus();
		if (statusCode == 200){
			String value = response.readEntity(String.class);
			response.close();
			return value;
		}else {
			try {
				checkResponse(statusCode);
				return null;
			} finally {
				response.close();
			}
		}
	}


	public void putValue(String group, String service, String key, String value) throws MWWebServiceException {
		Response response = this.client
						.path(group)
						.path(service)
						.path(key)
						.request().put(Entity.text(value));
		int statusCode = response.getStatus();
		if (statusCode == 200){
			response.close();
		}
		else {
			try {
				checkResponse(statusCode);
			} finally {
				response.close();
			}
		}
	}

	public void deleteValue(String group, String service, String key) throws MWWebServiceException {
		Response response = this.client
						.path(group)
						.path(service)
						.path(key)
						.request().delete();
		int statusCode = response.getStatus();
		if (statusCode == 200){
			response.close();
		}else{
			try {
				checkResponse(statusCode);
			} finally {
				response.close();
			}
		}
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

	// ###################
	// # PATH STATISTICS #
	// ###################

	private void printPathStatistics() throws MWWebServiceException {
		System.out.println("  Group  |  #IDs  | #Calls");
		System.out.println("---------+--------+--------");
		String[] groups = listGroups();
		for(String group: groups) {
			// Get ID statistics
			String numberOfIDs;
			try {
				numberOfIDs = getValue(group, "path", "number-of-ids");
			} catch(MWWebServiceException wse) {
				numberOfIDs = "";
			}

			// Get call statistics
			String numberOfCalls;
			try {
				numberOfCalls = getValue(group, "path", "number-of-calls");
			} catch(MWWebServiceException wse) {
				numberOfCalls = "";
			}

			// Print statistics
			if(numberOfIDs.isEmpty() && numberOfCalls.isEmpty()) continue;
			System.out.println(String.format("%-8s | %6s | %6s", group, numberOfIDs, numberOfCalls));
		}
	}


	// #########
	// # SHELL #
	// #########

	@Override
	protected boolean processCommand(String[] args) throws MWWebServiceException {
		switch(args[0]) {
		case "help":
		case "h":
			System.out.println("The following commands are available:\n"
					+ "  help\n"
					+ "  list-groups\n"
					+ "  list-services <group>\n"
					+ "  create-service <group> <service>\n"
					+ "  delete-service <group> <service>\n"
					+ "  list-keys <group> <service>\n"
					+ "  get-value <group> <service> <key>\n"
					+ "  put-value <group> <service> <key> <value>\n"
					+ "  delete-value <group> <service> <key>\n"
					+ "  path-statistics\n"
					+ "  quit"
			);
			break;
		case "list-groups":
		case "lg":
			String[] groups = listGroups();
			for(String group: groups) System.out.println(group);
			break;
		case "list-services":
		case "ls":
			if(args.length < 2) throw new IllegalArgumentException("Usage: list-services <group>");
			String[] services = listServices(args[1]);
			for(String service: services) System.out.println(service);
			break;
		case "create-service":
		case "cs":
			if(args.length < 3) throw new IllegalArgumentException("Usage: create-service <group> <service>");
			createService(args[1], args[2]);
			break;
		case "delete-service":
		case "ds":
			if(args.length < 3) throw new IllegalArgumentException("Usage: delete-service <group> <service>");
			deleteService(args[1], args[2]);
			break;
		case "list-keys":
		case "l":
			if(args.length < 3) throw new IllegalArgumentException("Usage: list-keys <group> <service>");
			String[] keys = listKeys(args[1], args[2]);
			for(String key: keys) System.out.println(key);
			break;
		case "get-value":
		case "g":
			if(args.length < 4) throw new IllegalArgumentException("Usage: get-value <group> <service> <key>");
			String value = getValue(args[1], args[2], args[3]);
			System.out.println(value);
			break;
		case "put-value":
		case "p":
			if(args.length < 5) throw new IllegalArgumentException("Usage: put-value <group> <service> <key> <value>");
			putValue(args[1], args[2], args[3], args[4]);
			break;
		case "delete-value":
		case "d":
			if(args.length < 4) throw new IllegalArgumentException("Usage: delete-value <group> <service> <key>");
			deleteValue(args[1], args[2], args[3]);
			break;
		case "path-statistics":
		case "ps":
			printPathStatistics();
			break;
		case "exit":
		case "quit":
		case "x":
		case "q":
			return false;
		default:
			throw new IllegalArgumentException("Unknown command: " + args[0] + "\nUse \"help\" to list available commands");
		}
		return true;
	}


	// ########
	// # MAIN #
	// ########

	public static void main(String[] args) {
		MWRegistryClient registry = new MWRegistryClient();
		registry.shell();
	}

}
