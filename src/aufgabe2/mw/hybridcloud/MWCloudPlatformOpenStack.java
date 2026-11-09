package mw.hybridcloud;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.model.compute.BDMDestType;
import org.openstack4j.model.compute.BDMSourceType;
import org.openstack4j.model.compute.BlockDeviceMappingCreate;
import org.openstack4j.model.compute.Flavor;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.compute.Server.Status;
import org.openstack4j.model.compute.ServerCreate;
import org.openstack4j.model.network.Network;

public class MWCloudPlatformOpenStack implements MWCloudPlatform {

  private static final String AUTH_URL;
  private static final String APPLICATION_CREDENTIAL_ID;
  private static final String APPLICATION_CREDENTIAL_SECRET;

  static {
    String authUrl = System.getenv("OS_AUTH_URL");
    String appCredId = System.getenv("OS_APPLICATION_CREDENTIAL_ID");
    String appCredSecret = System.getenv("OS_APPLICATION_CREDENTIAL_SECRET");

    if (authUrl == null || appCredId == null || appCredSecret == null) {
      throw new IllegalStateException(
          "Unauthenticated, set OS_AUTH_URL, OS_APPLICATION_CREDENTIAL_ID and OS_APPLICATION_CREDENTIAL_SECRET to the values from app-cred-*-openrc.sh");
    }

    AUTH_URL = authUrl;
    APPLICATION_CREDENTIAL_ID = appCredId;
    APPLICATION_CREDENTIAL_SECRET = appCredSecret;
  }

  private final OSClientV3 client;

  public MWCloudPlatformOpenStack() throws MWCloudException {
    this.client =
        MWOpenStackAuth.authenticate(
            AUTH_URL, APPLICATION_CREDENTIAL_ID, APPLICATION_CREDENTIAL_SECRET);
  }

  private MWVirtualMachine serverToVM(Server server) {
    MWVirtualMachine vm =
        new MWVirtualMachine(server.getId(), server.getName(), server.getAccessIPv6());
    vm.lastState = server.getStatus().toString();
    return vm;
  }

  private Network getNetworkByName(String networkName) throws MWCloudException {
    for (Network network : this.client.networking().network().list()) {
      if (network.getName().equals(networkName)) {
        return network;
      }
    }
    throw new MWCloudException("no network found with name \"" + networkName + "\"");
  }

  private Flavor getFlavorByName(String flavorName) throws MWCloudException {
    for (Flavor flavor : this.client.compute().flavors().list()) {
      if (flavor.getName().equals(flavorName)) {
        return flavor;
      }
    }
    throw new MWCloudException("no flavor found with name \"" + flavorName + "\"");
  }

  private void log(String format, Object... args) {
    System.err.printf("[OSC] " + format + "\n", args);
  }

  private void waitFor(Duration duration) throws MWCloudException {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException ie) {
      throw new MWCloudException("interrupted while waiting for instance", ie);
    }
  }

  @Override
  public MWVirtualMachine startVM(MWVirtualMachineConfig conf) throws MWCloudException {
    BlockDeviceMappingCreate bdmc =
        Builders.blockDeviceMapping()
            .uuid(conf.snapshotId)
            .sourceType(BDMSourceType.SNAPSHOT)
            .destinationType(BDMDestType.VOLUME)
            .bootIndex(0)
            .deleteOnTermination(true)
            .build();

    String networkId =
        conf.networkId != null ? conf.networkId : this.getNetworkByName(conf.networkName).getId();
    this.log("resolved network name %s to ID %s", conf.networkName, networkId);
    String flavorId =
        conf.flavorId != null ? conf.flavorId : this.getFlavorByName(conf.flavorName).getId();
    this.log("resolved flavor name %s to ID %s", conf.flavorName, flavorId);

    ServerCreate sc =
        Builders.server()
            .name(conf.vmName)
            .networks(Arrays.asList(networkId))
            .flavor(flavorId)
            .addSecurityGroup(conf.securityGroup)
            .blockDevice(bdmc)
            .userData(Base64.getEncoder().encodeToString(conf.userData.getBytes()))
            .build();

    Server server = this.client.compute().servers().bootAndWaitActive(sc, 60 * 1000);
    if (server == null) {
      throw new MWCloudException("failed to create server");
    }
    if (server.getStatus() != Status.ACTIVE) {
      throw new MWCloudException("failed to activate server, status = " + server.getStatus());
    }
    return this.serverToVM(server);
  }

  @Override
  public void deleteVM(MWVirtualMachine vmRef) throws MWCloudException {
    this.client.compute().servers().delete(vmRef.vmId);

    Server server = this.client.compute().servers().get(vmRef.vmId);
    while (server != null && server.getStatus() != Status.DELETED) {
      this.waitFor(Duration.ofSeconds(1));
      server = this.client.compute().servers().get(vmRef.vmId);
      if (server != null) {
        this.log("status = %s", server.getStatus());
      }
    }
    if (server == null) {
      this.log("server is null");
    }
  }

  @Override
  public List<MWVirtualMachine> listVMs() throws MWCloudException {
    return this.client.compute().servers().list().stream()
        .map(this::serverToVM)
        .collect(Collectors.toList());
  }

  @Override
  public Double getCPUUsage(MWVirtualMachine vm, int seconds) throws MWCloudException {
    /*
     *  TODO: Implement method (optional for 5.0 ECTS)
     */
    return null;
  }

  public static void main(String[] args) {
    final String FLAVOR_NAME = "SCS-1V-4";
    final String FLAVOR_ID = null; // null means "figure it out"
    final String SNAPSHOT_ID = "368f7ae5-8430-40a3-8f5d-208be1563e18";
    final String NETWORK_NAME = "default"; // https://cc.rrze.de/project/networks/
    final String NETWORK_ID = null; // null means "figure it out"

    // default from https://cc.rrze.de/project/security_groups/
    final String SECURITY_GROUP_ID = "48f6cbac-d682-440b-a3b3-9b0c2991742d";

    MWVirtualMachineConfig conf =
        new MWVirtualMachineConfig(
            "VM_NAME",
            "IMAGE_NAME",
            "IMAGE_ID",
            "SNAPSHOT_NAME",
            SNAPSHOT_ID,
            FLAVOR_NAME,
            FLAVOR_ID,
            NETWORK_NAME,
            NETWORK_ID,
            SECURITY_GROUP_ID,
            "KEY_NAME",
            "USER_DATA");

    try {
      MWCloudPlatformOpenStack platform = new MWCloudPlatformOpenStack();

      MWVirtualMachine vm = platform.startVM(conf);
      System.out.println("startVM: " + vm);

      for (MWVirtualMachine vm_ : platform.listVMs()) {
        System.out.println("listVMs: " + vm_);
        platform.deleteVM(vm_);
      }

      platform.deleteVM(vm);
      System.out.println("deleteVM: ok");

      for (MWVirtualMachine vm_ : platform.listVMs()) {
        System.out.println("listVMs: " + vm_);
      }
    } catch (MWCloudException ce) {
      System.out.println("error: " + ce);
    }
  }
}
