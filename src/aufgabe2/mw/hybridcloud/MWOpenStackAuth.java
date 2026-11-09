package mw.hybridcloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.core.transport.ObjectMapperSingleton;
import org.openstack4j.openstack.OSFactory;
import org.openstack4j.openstack.identity.v3.domain.KeystoneToken;

/**
 * Authentication at OpenStack with an application credential.
 *
 * <p>openstack4j does not support application credentials, so the token is requested from Keystone
 * directly. The parameters correspond to the variables in the openrc file of the application
 * credential.
 *
 * <p>The token expires (usually after one hour) and the returned client cannot renew it. Call
 * {@link #authenticate} again before the expiry ({@code client.getToken().getExpires()}) or after a
 * request failed with HTTP 401.
 */
public class MWOpenStackAuth {

  /**
   * @param authUrl Keystone endpoint ({@code OS_AUTH_URL}), with or without {@code /v3}
   * @param applicationCredentialId {@code OS_APPLICATION_CREDENTIAL_ID}
   * @param applicationCredentialSecret {@code OS_APPLICATION_CREDENTIAL_SECRET}
   */
  public static OSClientV3 authenticate(
      String authUrl, String applicationCredentialId, String applicationCredentialSecret)
      throws MWCloudException {
    // The openrc may contain the unversioned endpoint, the openstack CLI discovers the version
    // itself
    String endpoint = authUrl.endsWith("/") ? authUrl : authUrl + "/";
    if (!endpoint.endsWith("/v3/")) {
      endpoint += "v3/";
    }

    // Application credentials are bound to a project and must not request a scope themselves
    ObjectNode request = new ObjectMapper().createObjectNode();
    ObjectNode identity = request.putObject("auth").putObject("identity");
    identity.putArray("methods").add("application_credential");
    identity
        .putObject("application_credential")
        .put("id", applicationCredentialId)
        .put("secret", applicationCredentialSecret);

    try (Response response =
        ClientBuilder.newClient()
            .target(endpoint)
            .path("auth/tokens")
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.json(request.toString()))) {
      if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
        throw new MWCloudException(
            "Authentication at "
                + endpoint
                + " failed: "
                + response.getStatus()
                + " "
                + response.readEntity(String.class));
      }

      KeystoneToken token =
          ObjectMapperSingleton.getContext(KeystoneToken.class)
              .readValue(response.readEntity(String.class), KeystoneToken.class);
      token.setId(response.getHeaderString("X-Subject-Token"));
      token.setEndpoint(endpoint);
      return OSFactory.clientFromToken(token);
    } catch (IOException | ProcessingException e) {
      throw new MWCloudException("Authentication at " + endpoint + " failed", e);
    }
  }
}
